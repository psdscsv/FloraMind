# 题库包自定义题型说明

题库包目录下新增一个 `question_types.json`，就可以在**这个包内**使用自定义题型。
目前内置支持 4 种作答方式：多小问（parts）、整段作答（single）、多选题（multi_choice）、图文作答（image_text）。

```
自定义题型示例/
├── questions.json        # 题目
├── question_types.json   # 自定义题型定义
├── images/               # 图片（图文题/填图题按相对路径引用）
│   └── paramecium.png
├── practice.json         # 做题记录（App 自动生成，导出时一起带走）
```

## 1. question_types.json 格式

数组（也支持单个对象）：

```json
[
  {
    "id": "multi_choice",
    "name": "多选题",
    "description": "从多个选项中勾选全部正确答案",
    "layout": "multi_choice",
    "grading": "self"
  }
]
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 是 | 题型 id，题目里用 `customType` 引用它（同一包内唯一） |
| `name` | 否 | 显示名，缺省时用 `id` |
| `description` | 否 | 说明文字 |
| `layout` | 否 | `parts`（默认）/ `single` / `multi_choice` / `image_text`，写错按 `parts` 处理 |
| `grading` | 否 | `self`（默认，自我判定）/ `ai`（额外提供 AI 评分按钮，需配置 API Key；`multi_choice` 为自动判分，不需要） |

## 2. 题目格式

自定义题型的题目 `type` 写 `CUSTOM`，并用 `customType` 指向上面的 `id`。

### 2.1 多小问（layout = parts）

```json
{
  "id": 1,
  "type": "CUSTOM",
  "customType": "multi_part",
  "stem": "比较腔肠动物与扁形动物…",
  "parts": [
    { "stem": "（1）体制与对称方式有何不同？", "answer": "…", "keywords": ["辐射对称"] },
    { "stem": "（2）胚层数量有何差异？", "answer": "…" }
  ]
}
```

- `stem`：整题题干（材料/总述），显示在所有小问上方。
- `parts`：小问数组，每项有 `stem`、`answer`、`keywords`（后两个可省略）。

### 2.2 整段作答（layout = single）

不需要 `parts`，按普通 `correctAnswer` / `keywords` 写：

```json
{
  "id": 3, "type": "CUSTOM", "customType": "case_analysis",
  "stem": "某池塘春季出现大量藻类繁殖…",
  "correctAnswer": "原因：…；治理：…",
  "keywords": ["富营养化", "溶解氧"]
}
```

### 2.3 多选题（layout = multi_choice）

用 `options` 给选项，`correctOptions` 给**全部**正确选项（内容要与 `options` 完全一致，顺序无关）：

```json
{
  "id": 5,
  "type": "CUSTOM",
  "customType": "multi_choice",
  "stem": "下列属于昆虫纲特征的有（ ）",
  "options": [
    "身体分头、胸、腹三部分",
    "具三对足",
    "具两对翅",
    "用鳃呼吸",
    "体表具外骨骼"
  ],
  "correctOptions": [
    "身体分头、胸、腹三部分",
    "具三对足",
    "体表具外骨骼"
  ]
}
```

- 做题时勾选多个选项，点「提交答案」后自动判分：所选集合与 `correctOptions` 完全一致才算对。
- 没写 `correctOptions` 时不会显示「提交答案」，可以「显示答案」后自我判定。

### 2.4 图文作答（layout = image_text）

`imageName` 用相对题库包目录的图片路径，`stem` 是图片下方的描述/提问，答案写 `correctAnswer`、`keywords`：

```json
{
  "id": 7,
  "type": "CUSTOM",
  "customType": "image_text",
  "stem": "观察下图草履虫的结构，描述与运动、摄食、排泄有关的细胞器及其功能。",
  "imageName": "images/paramecium.png",
  "correctAnswer": "运动：纤毛摆动…；摄食：口沟…；排泄：伸缩泡…",
  "keywords": ["纤毛", "口沟", "食物泡", "伸缩泡"]
}
```

图片会渲染在题干下方（高度约 240dp），找不到图片时显示「图片未找到」占位提示，不影响作答。

## 3. 兜底规则

- 自定义题型**没写 `parts`** 时，整题退化成「一个作答框」，不会白屏。
- `parts` 里没有答案的小问，答案区显示「（无参考答案）」，仍可自我判定。
- 题型文件缺失/写错、`layout` 未知时，按 `parts` 兜底，不影响其他题库包。
- 多选题的 `correctOptions` 与 `options` 对不上时，答案区会尽量按字母展示能匹配上的项。

## 4. 做题与记录

- 做题记录仍按 **一道题一条记录** 存进 `practice.json`，所以错题本、导出、只刷错题的行为和普通题型一致。
- 多选题提交后立即判定；图文题/多小问默认「显示答案 + 自我判定」，`grading: "ai"` 时还可以点「AI 评分」。
- 导出的 ZIP 会自动带上 `question_types.json` 和 `images/`，导入到另一台设备后自定义题型照常可用。

## 5. 导入方式

把 `custom_types_sample/` 打成 ZIP（ZIP 内保留这一层目录），在 App 的「题库」页点右上角导入即可，
导入后会生成名为 `custom_types_sample` 的题库包。
