package com.createTemplate.model.base.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * @ClassName TWechatEwmVO
 * @Author libiqi
 * @Version 1.0
 */
@Data
public class TWechatEwmVO implements Serializable {
    @ApiModelProperty(value = "最大32个可见字符，只支持数字，大小写英文以及部分特殊字符：!#$&'()*+,/:;=?@-._~，其它字符请自行编码为合法字符（因不支持%，中文无法使用 urlencode 处理，请使用其他编码方式")
    private String scene;
    @ApiModelProperty(value = "必须是已经发布的小程序存在的页面（否则报错），例如 pages/index/index, 根路径前不要填加 /,不能携带参数（参数请放在scene字段里），如果不填写这个字段，默认跳主页面")
    private String page;
    @ApiModelProperty(value = "二维码的宽度，单位 px，最小 280px，最大 1280px")
    private Integer width;
    @ApiModelProperty(value = "自动配置线条颜色，如果颜色依然是黑色，则说明不建议配置主色调，默认 false")
    private Boolean auto_color;
    @ApiModelProperty(value = "是否需要透明底色，为 true 时，生成透明底色的小程序")
    private Boolean is_hyaline;
}