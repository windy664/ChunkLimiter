<div align="center">

<img src="assets/banner.svg" alt="ChunkLimiter" width="100%">

# ChunkLimiter

**区块数量限制** —— 监控区块内的实体/方块数量，超出上限自动拦截，防刷屏防卡服。

![Platform](https://img.shields.io/badge/Platform-Spigot%20%2F%20Paper-62bda8?style=flat-square&logo=minecraft&logoColor=white)
![Minecraft](https://img.shields.io/badge/MC-1.21-8a9a5b?style=flat-square)
![Java](https://img.shields.io/badge/Java-17%2B-f89820?style=flat-square&logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-3da639?style=flat-square)

[功能](#-功能) · [配置](#️-配置) · [构建](#-构建)

</div>

## ✨ 功能

- **区块限额**：`ChunkLimiterListener` 在放置/生成时统计所在区块的计数，超过配置上限直接取消
- **可配置上限**：限额项与数值全部走 `Config`，按服务器规模自行调整
- **低开销**：只在实际变更事件上计数，不做全服扫描

## ⚙️ 配置

`config.yml` 中按类型配置各区块上限（方块/实体等），超出即拦截并提示。

## 🔨 构建

```bash
./gradlew build
```

## 📄 许可

[MIT](LICENSE)
