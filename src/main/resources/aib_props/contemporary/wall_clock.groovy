// round wall clock, 0.6 across, its middle 2.2 above the floor; the wall is at z = 0
def cy = 2.2
// a round disc out of four squares turned in steps of 22.5 degrees
for (int i = 0; i < 4; i++) {
    def a = i * 22.5
    ai.addPart('#2e2e2e', -0.3, cy - 0.3, -0.07, 0.6, 0.6, 0.07, 0, 0, a)      // rim
    ai.addPart('#f6f3ea', -0.26, cy - 0.26, -0.075, 0.52, 0.52, 0.01, 0, 0, a) // face
}
for (int i = 0; i < 12; i++) {
    def t = Math.toRadians(i * 30)
    def r = 0.215
    def len = i % 3 == 0 ? 0.07 : 0.04
    def cx = r * Math.sin(t)
    def cz = cy + r * Math.cos(t)
    ai.addPart('#2e2e2e', cx - 0.008, cz - len / 2, -0.08, 0.016, len, 0.006, 0, 0, -i * 30)   // hour marks
}
// hour hand toward ten, minute hand toward two
def ha = Math.toRadians(-60)
ai.addPart('#1a1a1a', 0.065 * Math.sin(ha) - 0.012, cy + 0.065 * Math.cos(ha) - 0.065, -0.086, 0.024, 0.13, 0.006, 0, 0, 60)
def ma = Math.toRadians(60)
ai.addPart('#1a1a1a', 0.095 * Math.sin(ma) - 0.008, cy + 0.095 * Math.cos(ma) - 0.095, -0.09, 0.016, 0.19, 0.006, 0, 0, -60)
ai.addPart('#b33a2a', -0.004, cy - 0.12, -0.094, 0.008, 0.2, 0.004)          // second hand straight down
ai.addPart('#1a1a1a', -0.02, cy - 0.02, -0.096, 0.04, 0.04, 0.01, 0, 0, 45)   // middle cap
