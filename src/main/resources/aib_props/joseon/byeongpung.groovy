// 병풍: a six-panel folding screen, 2 wide and 1.4 high, standing in a zigzag in front of the wall at z = 0
def wood = '#3e2414'
def paper = '#efe4c8'
def ink = '#3a3a32'
int n = 6
def pw = 0.32
for (int i = 0; i < n; i++) {
    def x = -0.96 + i * pw
    def zc = (i % 2 == 0) ? -0.12 : -0.06
    def turn = (i % 2 == 0) ? 12 : -12
    ai.addPart(wood, x, 0.04, zc - 0.015, pw, 1.36, 0.03, turn, 0, 0)                 // frame
    ai.addPart(paper, x + 0.025, 0.14, zc - 0.022, pw - 0.05, 1.12, 0.01, turn, 0, 0)  // the painted paper
    // an ink-painted plum branch or mountain on each panel
    ai.addPart(ink, x + 0.08, 0.3 + (i % 3) * 0.12, zc - 0.03, 0.012, 0.5, 0.004, turn, 0, 15 - i * 6)
    ai.addPart(ink, x + 0.12, 0.62 + (i % 2) * 0.1, zc - 0.03, 0.12, 0.01, 0.004, turn, 0, -20)
    ai.addSphere('#c0392b', x + 0.16, 0.7 + (i % 2) * 0.1, zc - 0.035, 0.018)       // red plum blossoms
    ai.addSphere('#c0392b', x + 0.1, 0.78, zc - 0.035, 0.014)
    ai.addPart(wood, x + 0.03, 0, zc - 0.03, 0.04, 0.06, 0.06)                        // little feet
    ai.addPart(wood, x + pw - 0.07, 0, zc - 0.03, 0.04, 0.06, 0.06)
}
