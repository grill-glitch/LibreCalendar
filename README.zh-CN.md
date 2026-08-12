# LibreCalendar

安卓中文日历应用 — Kotlin + Jetpack Compose (Material 3)。
农历、节气、中国节假日与调休,离线优先,可选在线节假日数据。

[English](README.md)

## 功能

- **月 / 周 / 日 / 日程视图** — 月份滑动切换,点击日期查看当日事件卡片,点击卡片编辑(删除需确认)
- **农历与节气** — 内置天文算法引擎,完全离线
- **中国节假日与调休** — 数据源可配置:
  - **小米日历**(MIUI 日历接口)— `standard` 版默认
  - **holiday-cn**([NateScarlet/holiday-cn](https://github.com/NateScarlet/holiday-cn))
    — 开放数据,支持**自定义镜像**(设置 → 日历数据源)
- **事件** — 增删改、提醒、重复事件
- **标签与颜色** — 事件颜色取标签色 / 自定义色 / 标题哈希动态色(同名同色);标签页管理标签与图标
- **动态日期图标** — 桌面图标显示当天日期(兼容任意启动器)
- **24 小时时间轴日视图** — 点击时间轴选中时刻,新建事件默认使用选中时间

## 版本风味

| 风味 | 包名 | 节假日数据源 |
|---|---|---|
| `standard` | `org.librelab.calendar` | 小米日历接口(默认),可选 holiday-cn |
| `libre` | `org.librelab.calendar.libre` | holiday-cn(默认),完全离线兜底 |

两个风味在离线时都会回退到内置本地节假日引擎。

## 构建

```bash
# release(存在 keystore.properties 时自动签名)
./gradlew :app:assembleStandardRelease :app:assembleLibreRelease
```

需要 JDK 17+、Android SDK 36、AGP 9.3.1 / Kotlin 2.3.21。

## 许可证

Apache-2.0
