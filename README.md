# 多计数器 MultiCounter

一个轻量的 Android 多计数器应用：同时管理多个计数项，支持增删、置顶、三态交互与位移动效。

## 技术栈

- Java 17 + Android 原生 View 体系
- Material 3 设计规范
- SharedPreferences 本地持久化

## 功能

- **多计数项**：一屏管理多个计数器，各自独立累加 / 重置
- **位移动效**：自研 `ItemAnimator` 实现新增与删除时的列表位移动画，避免默认动画的跳变
- **三态交互**：卡片在常态、选中、编辑三种状态间平滑切换
- **可靠落盘**：SharedPreferences 异步写入，异常中断时对脏数据做兜底校验

## 目录结构

```
app/src/main/java/com/example/multicounter/   业务代码
app/src/main/res/layout/                      页面布局
app/src/main/res/values/                      主题与字符串
```

## 运行

用 Android Studio 打开根目录即可运行，无需额外配置。

> `local.properties` 记录本机 Android SDK 路径，不纳入版本管理。
