# 题库包自定义题型说明

题库包目录下新增一个 `question_types.json`，就可以在**这个包内**使用自定义题型。

```
多小问示例/
├── questions.json        # 题目
├── question_types.json   # 自定义题型定义
├── practice.json         # 做题记录（App 自动生成，导出时一起带走）
└── images/…              # 可选资源
```

## 1. question_types.json 格式

数组（也支持单个对象）：

```json
[
  {
    "id": "multi_part",
    "name": "多小问",
    "description": "一道大题下分多个小问",
    "layout": "parts",
    "grading": "self"
  }
]
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 是 | 题型 id，题目里用 `customType` 引用它（同一包内唯一） |
| `name` | 否 | 显示名，缺省时用 `id` |
| `description` | 否 | 说明文字 |
| `layout` | 否 | 作答方式：`parts`（默认，多小问） / `single`（一段完整作答） |
| `grading` | 否 | `self`（默认，自我判定） / `ai`（额外提供 AI 评分按钮，需配置 API Key） |

## 2. 题目格式

自定义题型的题目 `type` 写 `CUSTOM`，并用 `customType` 指向上面的 `id`：

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

- `stem`：整题题干（材料/总述），会显示在所有小问上方。
- `parts`：小问数组，每个小问有 `stem`、`answer`、`keywords`（后两个可省略）。
- `layout: single` 的题型不需要 `parts`，按普通 `correctAnswer` / `keywords` 写即可。

### 兜底规则

- 自定义题型**没写 `parts`** 时，整题退化成「一个作答框」，不会白屏。
- `parts` 里没有答案的小问，答案区显示「（无参考答案）」，仍可自我判定。
- 题型文件缺失/写错时，题目按兜底方式作答，不影响其他题库包。

## 3. 做题与记录

- 多小问题型：每个小问一个输入框，点「显示答案」逐个小问给出参考答案，再整体点「答对了 / 答错了」。
- 做题记录仍然按 **一道题一条记录** 存进 `practice.json`，所以错题本、导出、只刷错题的行为都和普通题型一致。
- 导出的 ZIP 会自动带上 `question_types.json`，导入到另一台设备后自定义题型照常可用。

## 4. 导入方式

把 `multi_part_sample/` 打成 ZIP（ZIP 内保留这一层目录），在 App 的「题库」页点右上角导入即可，
导入后会生成名为 `multi_part_sample` 的题库包。
