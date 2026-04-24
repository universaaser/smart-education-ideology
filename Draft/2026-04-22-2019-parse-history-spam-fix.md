# ResourceUpload 历史列表请求刷屏修复

## 背景

后端控制台每 1~3 秒持续出现：

```
SELECT COUNT(*) AS total FROM parse_tasks WHERE (user_id = ?)
Parameters: 1(Long)
```

用户侧观察：在 Resource Upload 页面停留时，"文档历史记录"区域像在轮询后端。代码里并不存在任何显式 `setInterval` 调用 `uploadApi.listTasks`，唯一周期性的 `setInterval` 在
`layouts/TeacherShell.tsx:34`（2s 抓取状态轮询），它不直接查 `parse_tasks`，但会让 `TeacherShell` 每 2s 重渲染一次。

## 根因判断

1. 仅凭静态代码无法 100% 定位上游为何把 `useEffect([userId])` 反复驱动（怀疑某条路径使得 `userId` 身份在初次渲染中抖动，或 Vite HMR / 某个 StrictMode 交互导致 effect 重跑）。
2. 但确实存在一个可观察 bug：`isUnmountedRef` 只在 cleanup 置 true，未在 mount 重置。React 18 dev StrictMode 首次 `mount → unmount → remount` 后该 ref 永久为 true，后续所有 `setState` 被静默丢弃。

## 改动（最小面）

文件：`views/ResourceUpload.tsx`

- 挂载 effect 中显式 `isUnmountedRef.current = false`，修复 StrictMode 残留 true 的问题。
- `loadHistoryTasks` 增加两层守卫：
  - 在途请求重入直接忽略（`historyInFlightRef`）。
  - 1 秒节流窗口（`historyLastLoadAtRef`），任何 1s 内的重复触发直接丢弃。
- 相关 ref 在组件顶部声明（`historyInFlightRef`, `historyLastLoadAtRef`）。

行为影响：

- 正常用户操作（挂载加载、Refresh 按钮、上传完成后刷新）不受影响，1s 节流足够覆盖正常节奏。
- 任何异常的高频重驱动（父级轮询 / StrictMode / 意外 re-render）都会被挡掉。

不改动：

- 未改 `TeacherShell` 的 2s 抓取状态轮询。
- 未改 effect 的 deps。
- 未改 `listTasks` 接口/后端 SQL。

## 待验证

- 进入 Resource Upload 页后观察后端日志，`SELECT COUNT(*) FROM parse_tasks` 是否不再每隔数秒出现。
- 若仍持续出现，说明真实上游驱动源不是经 `loadHistoryTasks` 发出的（例如浏览器缓存 / 其他组件 / SpringDevTools 热重启等），需要打开浏览器 Network 面板核对 `/upload/tasks?userId=...` 的请求时间轴。

## 后续可选收尾

- 若用户明确确认仍存在 1~3s 节律，再去定位真实驱动源（很可能是 `TeacherShell` 某条间接路径让 `ResourceUpload` 的 `[userId]` effect 被重跑；可以在 dev 下临时加 console 打印 `userId` 引用变化）。
- 可以考虑把 `loadHistoryTasks` 的 fetch 与 UI loading 分开：仅在真正拉到新数据时更新列表，避免空结果反复触发 `setState`。
