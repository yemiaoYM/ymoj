import Storage from '@/utils/storage';

const REMEMBERED_USERNAME_KEY = 'RememberedUsername';

interface PasswordCredentialData {
  id: string;
  password: string;
}

type PasswordCredentialConstructor = new (data: PasswordCredentialData) => Credential;

// 密码由浏览器密码管理器保存，本地存储仅记录用户名。
const getPasswordCredentialConstructor = () => {
  return (window as Window & { PasswordCredential?: PasswordCredentialConstructor }).PasswordCredential;
};

export function getRememberedUsername(): string {
  try {
    const username = Storage.get<unknown>(REMEMBERED_USERNAME_KEY);
    return typeof username === 'string' ? username : '';
  } catch {
    return '';
  }
}

export function clearRememberedLogin(): void {
  try {
    Storage.remove(REMEMBERED_USERNAME_KEY);
  } catch {
    // 浏览器禁用本地存储时不影响登录。
  }
}

export async function getRememberedPassword(username: string): Promise<string> {
  if (!username || !window.isSecureContext || !navigator.credentials || !getPasswordCredentialConstructor()) {
    return '';
  }
  try {
    const options: CredentialRequestOptions & { password: boolean } = {
      password: true,
      mediation: 'silent'
    };
    const credential = await navigator.credentials.get(options);
    if (credential?.type !== 'password' || credential.id !== username) {
      return '';
    }
    const password = (credential as Credential & { password?: string }).password;
    return typeof password === 'string' ? password : '';
  } catch {
    // 浏览器拒绝或无法填充凭据时，由用户手动输入。
    return '';
  }
}

export async function rememberLogin(username: string, password: string): Promise<void> {
  try {
    Storage.set(REMEMBERED_USERNAME_KEY, username);
  } catch {
    // 本地存储不可用时，仍可使用浏览器密码管理器。
  }
  const PasswordCredential = getPasswordCredentialConstructor();
  if (!window.isSecureContext || !navigator.credentials || !PasswordCredential) {
    return;
  }
  try {
    await navigator.credentials.store(new PasswordCredential({ id: username, password }));
  } catch {
    // 保存凭据失败不改变已成功的登录结果。
  }
}
