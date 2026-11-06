#define _POSIX_C_SOURCE 200809L

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

/* 两个独立输出管道共用预算，父进程的描述符禁止通过 /proc 绕过。 */
static int write_all(int descriptor, const char *buffer, size_t length) {
    size_t offset = 0;
    while (offset < length) {
        ssize_t written = write(descriptor, buffer + offset, length - offset);
        if (written < 0 && errno == EINTR) {
            continue;
        }
        if (written <= 0) {
            return -1;
        }
        offset += (size_t) written;
    }
    return 0;
}

/* FD 3 只由可信启动器持有；exec 时关闭，不与用户 stdout/stderr 混用。 */
static void infrastructure_failure(const char *stage) {
    (void) write_all(3, stage, strlen(stage));
}

static int positive_limit(const char *text, unsigned long long *value) {
    char *end = NULL;
    if (text[0] < '1' || text[0] > '9') {
        return -1;
    }
    errno = 0;
    *value = strtoull(text, &end, 10);
    return errno == 0 && end != text && *end == '\0' && *value > 0 ? 0 : -1;
}

int main(int argc, char **argv) {
    if (argc == 2 && strcmp(argv[1], "--version") == 0) {
        puts("ymcloud-output-limit 2");
        return 0;
    }
    signal(SIGPIPE, SIG_IGN);
    int diagnostic_flags = fcntl(3, F_GETFD);
    if (diagnostic_flags < 0 || fcntl(3, F_SETFD, diagnostic_flags | FD_CLOEXEC) != 0) {
        infrastructure_failure("DESCRIPTOR\n");
        return 126;
    }
    unsigned long long output_limit;
    unsigned long long file_limit;
    if (argc < 4 || positive_limit(argv[1], &output_limit) != 0
            || positive_limit(argv[2], &file_limit) != 0 || file_limit > INT64_MAX) {
        infrastructure_failure("ARGUMENTS\n");
        return 126;
    }
    struct rlimit file_size = {(rlim_t) file_limit, (rlim_t) file_limit};
    if (setrlimit(RLIMIT_FSIZE, &file_size) != 0 || prctl(PR_SET_DUMPABLE, 0) != 0) {
        infrastructure_failure("SETUP\n");
        return 126;
    }
    int streams[2][2];
    if (pipe(streams[0]) != 0) {
        infrastructure_failure("PIPE\n");
        return 126;
    }
    if (pipe(streams[1]) != 0) {
        close(streams[0][0]);
        close(streams[0][1]);
        infrastructure_failure("PIPE\n");
        return 126;
    }
    pid_t child = fork();
    if (child < 0) {
        infrastructure_failure("FORK\n");
        for (int stream = 0; stream < 2; stream++) {
            close(streams[stream][0]);
            close(streams[stream][1]);
        }
        return 126;
    }
    if (child == 0) {
        if (signal(SIGPIPE, SIG_DFL) == SIG_ERR || setpgid(0, 0) != 0
                || prctl(PR_SET_PDEATHSIG, SIGKILL) != 0
                || dup2(streams[0][1], STDOUT_FILENO) < 0
                || dup2(streams[1][1], STDERR_FILENO) < 0) {
            infrastructure_failure("CHILD_SETUP\n");
            _exit(126);
        }
        for (int stream = 0; stream < 2; stream++) {
            close(streams[stream][0]);
            close(streams[stream][1]);
        }
        execvp(argv[3], argv + 3);
        infrastructure_failure("EXEC\n");
        _exit(126);
    }
    /* 双侧设置进程组避免父子调度顺序影响超限时的回收。 */
    if (setpgid(child, child) != 0 && errno != EACCES && errno != ESRCH) {
        infrastructure_failure("PROCESS_GROUP\n");
        kill(child, SIGKILL);
    }
    close(streams[0][1]);
    close(streams[1][1]);
    struct pollfd descriptors[2] = {
        {streams[0][0], POLLIN, 0},
        {streams[1][0], POLLIN, 0}
    };
    unsigned long long used = 0;
    int open_streams = 2;
    int failed = 0;
    while (open_streams > 0 && !failed) {
        int ready = poll(descriptors, 2, -1);
        if (ready < 0) {
            if (errno == EINTR) {
                continue;
            }
            infrastructure_failure("POLL\n");
            failed = 1;
            break;
        }
        for (int stream = 0; stream < 2; stream++) {
            if (descriptors[stream].fd < 0 || descriptors[stream].revents == 0) {
                continue;
            }
            char buffer[8192];
            ssize_t count = read(descriptors[stream].fd, buffer, sizeof(buffer));
            if (count < 0 && errno == EINTR) {
                continue;
            }
            if (count <= 0) {
                close(descriptors[stream].fd);
                descriptors[stream].fd = -1;
                open_streams--;
                if (count < 0) {
                    infrastructure_failure("READ\n");
                    failed = 1;
                }
                continue;
            }
            unsigned long long available = output_limit - used;
            size_t accepted = (unsigned long long) count > available ? (size_t) available : (size_t) count;
            if (accepted > 0 && write_all(stream + 1, buffer, accepted) != 0) {
                infrastructure_failure("WRITE\n");
                failed = 1;
                break;
            }
            used += accepted;
            if (accepted < (size_t) count) {
                failed = 1;
                break;
            }
        }
    }
    for (int stream = 0; stream < 2; stream++) {
        if (descriptors[stream].fd >= 0) {
            close(descriptors[stream].fd);
        }
    }
    if (failed) {
        kill(-child, SIGKILL);
        kill(child, SIGKILL);
    }
    int status;
    while (waitpid(child, &status, 0) < 0) {
        if (errno != EINTR) {
            infrastructure_failure("WAIT\n");
            return 126;
        }
    }
    if (failed) {
        return 125;
    }
    if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }
    return WIFEXITED(status) ? WEXITSTATUS(status) : 126;
}
