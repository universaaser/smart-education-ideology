# -*- coding: utf-8 -*-
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
from datetime import date

plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei']
plt.rcParams['axes.unicode_minus'] = False

# Sprint 1: 2026-09-08 ~ 2026-09-29, total 23 story points
start, end = date(2026, 9, 8), date(2026, 9, 29)

# 实际剩余 story points（依据 GitHub issue/PR/commit 时间线）
actual_points = [
    (date(2026, 9, 8),  23, 'Sprint 1 启动\n(选型、Fork、任务分配)'),
    (date(2026, 9, 15), 23, '9/18 组会：确定选型与分工'),
    (date(2026, 9, 22), 23, '性能组主开发启动'),
    (date(2026, 9, 26), 18, '#2 赵俊楠 回归脚本代码完成(3)\n#3 王文博 跨平台问题记录(2)'),
    (date(2026, 9, 27), 8,  '#4 王林睿 仪表盘优化完成并合并(5)\n#7 李洋 组件拆分代码完成(3)\n#9 晏一飞 JWT 设计准备(2)'),
    (date(2026, 9, 28), 8,  '#10 卢琦贺 分支提交代码\n(进行中，未完成不计)'),
    (date(2026, 9, 29), 5,  '#5 纪要+燃尽图+证据归档(3)\n剩余5分 = #10 未完成'),
]
ax_pts = [p for _, p, _ in actual_points]
ax_dates = [d for d, _, _ in actual_points]

# 理想燃尽线 23 -> 0
ideal_dates = [start, end]
ideal_pts = [23, 0]

fig, ax = plt.subplots(figsize=(11, 6.5), dpi=150)

ax.plot(ideal_dates, ideal_pts, '--', color='#999999', linewidth=1.8, label='理想燃尽线（23 → 0）')
ax.plot(ax_dates, ax_pts, '-o', color='#c0392b', linewidth=2.5, markersize=8, label='实际剩余 Story Points')

# 数据点标注
for d, p, note in actual_points:
    ax.annotate(f'{p}', (d, p), textcoords='offset points', xytext=(0, 10),
                ha='center', fontsize=11, fontweight='bold', color='#c0392b')

# 关键事件注释（错开摆放避免重叠）
offsets = {0: (10, 18), 1: (0, 14), 2: (10, 16), 3: (12, 20), 4: (-10, -55), 5: (8, 14), 6: (-30, -50)}
for i, (d, p, note) in enumerate(actual_points):
    dx, dy = offsets[i]
    ax.annotate(note, (d, p), textcoords='offset points', xytext=(dx, dy),
                fontsize=7.5, color='#333333',
                arrowprops=dict(arrowstyle='-', color='#aaaaaa', lw=0.7))

ax.set_title('Sprint 1 燃尽图（2026-09-08 ~ 2026-09-29）｜CS5351 Group 20', fontsize=14, fontweight='bold', pad=14)
ax.set_xlabel('日期', fontsize=11)
ax.set_ylabel('剩余 Story Points', fontsize=11)
ax.set_ylim(-2, 27)
ax.set_xlim(date(2026, 9, 6), date(2026, 10, 1))
ax.xaxis.set_major_formatter(mdates.DateFormatter('%m-%d'))
ax.xaxis.set_major_locator(mdates.DayLocator(interval=3))
ax.grid(True, linestyle=':', alpha=0.5)
ax.legend(loc='upper right', fontsize=10)

footer = ('总计 23 分｜完成 18 分｜Sprint 1 结束剩余 5 分（#10 getReport 优化，代码已在 feature/perf-report 分支，转入 Sprint 2 收尾）\n'
          '口径：story points 由轮值人按 issue/PR 完成时间补记；PR 以代码完成+review 为完成标准，#1、#8 合并待跟进')
fig.text(0.5, 0.015, footer, ha='center', fontsize=8, color='#666666')

plt.tight_layout(rect=[0, 0.05, 1, 1])
plt.savefig('C:/Users/Administrator/WorkBuddy/2026-09-30-14-36-42/sprint1-evidence/docs/sprint1/burndown-sprint1.png',
            bbox_inches='tight', facecolor='white')
print('saved')
