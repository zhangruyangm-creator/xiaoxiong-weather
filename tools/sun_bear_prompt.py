"""小熊风格锚定 prompt 与场景模板。

SUN_BEAR_STYLE_BLOCK: 风格描述，与 minimax_image_mcp.py 中的常量保持同步。
SCENE_PRESETS: 已有场景的命名 → 描述，便于复用 prompt。

风格锚定基于 ic_sun_bear_work_morning.webp（由 Nano Banana 生成）：
- 3D 渲染 / 软糯毛绒质感的卡通小熊
- 姜黄 / 蜂蜜色毛 + 奶油色嘴部
- 椭圆黑鼻 + 圆弧嘴
- 大圆豆豆眼 + 大圆耳（深棕色内耳）
- 大圆粉色腮红
- 头部 ~50% 身高，矮胖身体
- 画面占比 70-80%，背景是有意义的环境
"""
from __future__ import annotations

SUN_BEAR_STYLE_BLOCK = (
    "A cute 3D rendered cartoon sun bear character with soft plush toy-like texture: "
    "round chubby body, ginger / honey-orange fur (NOT light brown), "
    "large cream-colored muzzle area around the mouth, "
    "small black oval nose with a curved smile line below, "
    "BIG round black button eyes with single small white highlight dot, "
    "TWO LARGE ROUND EARS prominent on top of head with darker brown inner ear, "
    "large round soft pink blush on cheeks. "
    "Soft 3D volumetric rendered look, slightly rounded plastic-like forms, "
    "mild subsurface scattering on ears and cheeks, "
    "soft cel-shading only as subtle ambient occlusion, NOT flat 2D. "
    "Warm pastel palette: ginger orange, caramel brown, cream, soft blue, soft green. "
    "Children's picture book aesthetic with modern 3D rendering, "
    "Pixar-like soft plush toy style. "
    "Character fills 70-80% of frame, centered composition, "
    "single isolated scene with meaningful background, "
    "no text, no watermark, no floating decorative halos, "
    "square or 3:4 framing, soft warm lighting from upper-left."
)

# 已有场景的命名约定与简短描述（可作为 generate_sun_bear_illustration 的输入）
SCENE_PRESETS: dict[str, dict[str, str]] = {
    "work_farm": {
        "action": "harvesting carrots and vegetables in a vegetable garden",
        "setting": "sunny farm with red barn, wooden fence, green fields, distant hills",
        "props": "straw hat, blue overalls, yellow plaid shirt, basket with carrots/cabbage/tomato",
    },
    "work_guard": {
        "action": "standing watch as a security guard",
        "setting": "bright lobby with potted plants",
        "props": "guard uniform, badge, walkie-talkie",
    },
    "work_teacher": {
        "action": "teaching at a blackboard",
        "setting": "cheerful classroom with charts and student desks",
        "props": "glasses, pointer, stack of books",
    },
    "work_scientist": {
        "action": "mixing colorful liquids in a lab",
        "setting": "bright science lab with beakers and microscope",
        "props": "white lab coat, safety goggles, test tubes",
    },
    "home_balcony": {
        "action": "hanging a shirt on a clothesline, holding a wooden hanger",
        "setting": "open-air apartment balcony, balcony railing, blue sky with white clouds visible above, distant city rooftops",
        "props": "clothesline with hanging shirts, potted plants on the railing, sun hat on the bear's head",
    },
    "home_kitchen": {
        "action": "cooking breakfast on the stove",
        "setting": "cozy kitchen with wooden cabinets and morning light",
        "props": "apron, frying pan, spatula, fresh ingredients",
    },
    "home_bedroom": {
        "action": "waking up and stretching in bed",
        "setting": "warm bedroom with soft pastel bedding and morning light through curtains",
        "props": "pillow, blanket, small plush toy",
    },
    "home_study": {
        "action": "reading a book at a desk",
        "setting": "quiet study room with bookshelf and warm desk lamp",
        "props": "open book, glasses, cup of tea",
    },
    "commute_bus": {
        "action": "riding a crowded city bus",
        "setting": "early morning city street with red bus arriving at stop",
        "props": "small backpack, handrail",
    },
    "commute_metro": {
        "action": "standing in a subway car",
        "setting": "modern subway carriage with handrails",
        "props": "earphones, tote bag",
    },
    "commute_bike": {
        "action": "riding a bicycle to work",
        "setting": "shaded bike lane through tree-lined park",
        "props": "helmet, woven basket on bike",
    },
    "fest_spring": {
        "action": "holding a couplet at the doorway",
        "setting": "lantern-decorated courtyard with red decorations",
        "props": "red envelopes, paper cuttings",
    },
    "fest_mid_autumn": {
        "action": "admiring the full moon with mooncakes",
        "setting": "rooftop terrace with round table and lanterns",
        "props": "mooncakes, osmanthus tea, paper lantern",
    },
}


def build_prompt(scene: str, extra_action: str = "") -> str:
    """根据场景键生成完整 prompt。"""
    preset = SCENE_PRESETS.get(scene)
    if preset is None:
        return SUN_BEAR_STYLE_BLOCK + f" Scene: {scene}. {extra_action}".strip()

    parts = [SUN_BEAR_STYLE_BLOCK, f"Scene: {scene}."]
    if preset.get("action"):
        parts.append(f"Action: {preset['action']}.")
    if preset.get("setting"):
        parts.append(f"Setting: {preset['setting']}.")
    if preset.get("props"):
        parts.append(f"Props: {preset['props']}.")
    if extra_action:
        parts.append(f"Additional: {extra_action}")
    parts.append("Keep the bear's appearance identical to the reference image.")
    return " ".join(parts)
