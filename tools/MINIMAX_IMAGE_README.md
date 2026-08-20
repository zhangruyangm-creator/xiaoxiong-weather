# 小熊天气 AI 插画生成工具

> ⚠️ **状态：备用工具**
>
> 当前生成结果与项目主流的 2D 平面绘本风格（如 `ic_sun_bear_work_farm` 等）
> 不一致（MiniMax image-01 默认走 3D 渲染风）。在风格调通 + 与现有命名空间
> 对齐之前，本工具**只作备用**，不正式接入 `core:res-illustrations` 资源引用。
>
> 风格调通示例：`SUN_BEAR_STYLE_BLOCK` 已对齐 nano banana 生成的 `work_morning`
> 3D 风格，但仍需多轮 prompt 迭代 + 一张能进入生产使用的过审图。

通过 MiniMax 图像生成 API，为项目自动生成 `ic_sun_bear_*` 系列插画，
并自动落到 `core/res-illustrations/src/main/res/drawable-nodpi/`。

## 准备工作

1. 在 MiniMax 控制台生成 API key：
   - 国内大陆：`https://api.minimaxi.com`
   - 海外：`https://api.minimax.io`

2. 准备 Python 环境：
   ```bash
   pip install --user requests Pillow mcp
   ```

3. 配环境变量（推荐放进 `~/.zshrc`）：
   ```bash
   export MINIMAX_API_KEY="eyJhbGciOi..."
   export MINIMAX_IMAGE_ENDPOINT="https://api.minimaxi.com/v1/image_generation"  # 国内
   ```

## 终端工具（Python 脚本）

```bash
# 列出现有场景模板
python tools/gen_illustration.py --list-scenes

# 单张生成（用内置场景模板）
python tools/gen_illustration.py --scene home_balcony

# 批量生成
python tools/gen_illustration.py --scenes home_balcony,home_kitchen,commute_bike

# 自定义 prompt
python tools/gen_illustration.py \
    --prompt "小熊在做早餐，面前摆着煎蛋和培根" \
    --out ic_sun_bear_home_pancake

# 用 reference 图锁定小熊外观（推荐，避免每张图长得不一样）
python tools/gen_illustration.py \
    --scene home_balcony \
    --ref core/res-illustrations/src/main/res/drawable-nodpi/ic_sun_bear_work_farm.webp

# 走 IMAGE-01-LIVE + 风格（仅 image-01-live 支持 style）
python tools/gen_illustration.py \
    --scene home_balcony \
    --model image-01-live \
    --style '{"style_name":"Storybook"}'

# dry-run（不真发请求，只打印 payload）
python tools/gen_illustration.py --scene home_balcony --dry-run
```

### 输出

自动写到 `core/res-illustrations/src/main/res/drawable-nodpi/<out_name>.webp`，
尺寸 384x384，quality 90，与 `tools/export_sun_bear_webp.py` 保持一致。

## Cursor MCP 集成

配置 `~/.cursor/mcp.json`：

```json
{
  "mcpServers": {
    "minimax-image": {
      "command": "python3",
      "args": ["/Users/zhangruyang/androidProject/xiaoxiong-weather/tools/minimax_image_mcp.py"],
      "env": {
        "MINIMAX_API_KEY": "eyJhbGciOi..."
      }
    }
  }
}
```

重启 Cursor 后，你会看到以下工具：

- `generate_image(prompt, aspect_ratio, model, n, ref_image_url, style, ...)`
- `generate_sun_bear_illustration(scene, action, setting, props, out_name, ref_image, ...)`
  —— 自动拼风格锚定 prompt，落到 `core/res-illustrations` 默认目录
- `save_generated_image(image_url_or_b64, out_name, ...)`
- `list_existing_illustrations(prefix)` —— 列出现有插画

## 风格一致性策略

参考 `tools/sun_bear_prompt.py`：

1. **SUN_BEAR_STYLE_BLOCK** —— 基础风格描述，统一了小熊的体型 / 配色 / 表情
2. **SCENE_PRESETS** —— 已有场景的命名约定 + 简短描述
3. **subject_reference** —— 用 `ic_sun_bear_work_farm.webp` 作为参考图，
   API 会锁定小熊外观，生成"同一只小熊在不同场景"的图

## 命名规范

遵循项目已有约定：

```
ic_sun_bear_<场景>_<子场景>.webp

例子：
  ic_sun_bear_home_balcony.webp     # 阳台
  ic_sun_bear_work_farm.webp        # 农场
  ic_sun_bear_breakfast_baozi.webp  # 早餐·包子
```

## 后续接入代码

生成后还需要在 Kotlin 中引用。由于本项目用 `R as IllustR` 别名机制，
新资源命名加进别名即可，无需改 `R`。详见 `core/res-illustrations/build.gradle.kts`。
