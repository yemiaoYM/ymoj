package com.ymcloud.pojo.dto;

import com.ymcloud.pojo.enums.UserSex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

/**
 * @PackageName: com.ymcloud.pojo.dto
 * @ClassName: UpdateUserProfileDTO
 * @Author: Yemiao
 * @CreateTime: 2025-07-31  17:24
 * @Description: 修改当前用户基本信息请求体
 */
@Data
public class UpdateUserProfileDTO {
    /**
     * 用户昵称
     */
    @Size(min = 2, max = 20, message = "昵称长度在2到20之间")
    private String nickname;
    /**
     * 个性签名
     */
    @Size(max = 65535, message = "个性签名内容过长")
    private String signature;
    /**
     * 性别
     */
    private UserSex sex;
    /**
     * 学校
     */
    @Size(min = 2, max = 50, message = "学校名称长度在2到50之间")
    private String school;
    /**
     * 个人博客
     */
    @URL(message = "请输入有效的博客链接")
    @Size(max = 512, message = "博客链接过长")
    private String blog;
}
