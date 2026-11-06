// 벽 달력: a big paper wall calendar with a picture and the month's dates, about 1.5 up on the wall (z = 0)
def y = 1.1
ai.addPart('#f4f1ea', -0.25, y, -0.012, 0.5, 0.75, 0.01)              // paper
ai.addPart('#8fb8d8', -0.22, y + 0.42, -0.018, 0.44, 0.28, 0.004)     // picture: sky
ai.addPart('#5f8a4a', -0.22, y + 0.42, -0.02, 0.44, 0.1, 0.004)       // fields
ai.addSphere('#e8a8b8', 0.1, y + 0.6, -0.022, 0.04)                   // blossom tree
ai.addPart('#5a3a22', 0.09, y + 0.5, -0.022, 0.015, 0.08, 0.003)
ai.addPart('#a8322a', -0.2, y + 0.36, -0.018, 0.14, 0.04, 0.004)      // the month in red
// the dates: rows of little marks, Sundays red
for (int r = 0; r < 5; r++) {
    for (int c = 0; c < 7; c++) {
        ai.addPart(c == 0 ? '#c0392b' : '#3a3a3a', -0.21 + c * 0.06, y + 0.04 + r * 0.06, -0.018, 0.025, 0.025, 0.003)
    }
}
ai.addCylinder('#8a8a8a', 0, y + 0.74, -0.015, 0.012, 0.03)           // the nail it hangs from
