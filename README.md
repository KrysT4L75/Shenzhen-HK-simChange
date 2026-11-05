# 过关换卡 / Shenzhen–Hong Kong SIM Reminder

Android 1.2.0 已编译并签名。iOS 提供独立 SwiftUI 源码，尚未在 Xcode 编译或真机验证，不是已交付的 iPhone 安装包。

## 下载

[Android 1.2.0 APK](packages/GuoguanHuanka-1.2.0.apk) · [源码压缩包](packages/GuoguanHuanka-1.2.0-source.zip)

## 界面与口岸

首页集中显示守护开关、数据卡状态和换卡入口；其他配置收在右上角菜单。按钮使用一致圆角与裁切的点击反馈，取消系统默认阴影。

可选罗湖、福田／落马洲支线、深圳湾、皇岗、莲塘／香园围、文锦渡、沙头角。坐标为附近参考点，尚未实地校准；不代表口岸开放状态，也不用于国界判断。一次选择一个常用口岸。

## 平台支持

| 平台 | 实现 | 验证 |
|---|---|---|
| Android 8+ | 网络地区与双卡检查、位置提醒、前台守护 | 1.2.0 编译、签名及39项原有逻辑检查通过；1.0在Android16模拟器检查过。新版多口岸与界面仍需设备验证 |
| iOS 16+ | SwiftUI界面、系统位置触发通知、口岸选择 | 源码实现，未编译、未签名、未真机验证；无可靠双卡识别与自动切卡 |

## 构建

Android：Windows PowerShell 执行 `./setup-tools.ps1`，然后 `./build.ps1`。工具链下载并校验后保留在项目内。

iOS：需要 Mac 和 Xcode，安装 XcodeGen 后在 ios 目录执行 `xcodegen generate`，打开生成的工程，选择你自己的开发团队再构建。真机安装或 TestFlight 还需要相应 Apple 签名与分发配置。本项目没有提供 Apple 证书或已签名 IPA。

## 隐私和限制

应用不上传位置，无账号或广告。Android 不申请联网权限。提醒不能自动切卡或断网，也不能保证避免漫游费用。后台策略、精度、信号越界、勿扰和权限会影响提醒。

签名密钥与密码只在开发者本机 `.signing/`，永不上传。重新生成密钥的APK无法覆盖原签名安装。下载包不含密钥。

Apple依据：[位置通知](https://developer.apple.com/documentation/usernotifications/unlocationnotificationtrigger)、[iOS16.4电话运营商API限制](https://developer.apple.com/documentation/ios-ipados-release-notes/ios-ipados-16_4-release-notes)。
