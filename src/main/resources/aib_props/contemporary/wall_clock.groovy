// round wall clock, 0.6 across, its middle 2.2 above the floor; the wall is at z = 0
def cy = 2.2
// a round disc out of thin rows, each as wide as the circle at its height
int rows = 16
for (int j = 0; j < rows; j++) {
    def y0 = -0.3 + j * 0.6 / rows
    def ym = y0 + 0.3 / rows
    def v = 0.09 - ym * ym
    def half = Math.sqrt(v < 0 ? 0 : v)
    ai.addPart('#2e2e2e', -half, cy + y0, -0.07, 2 * half, 0.6 / rows + 0.002, 0.07)                 // rim
    def yf = -0.26 + j * 0.52 / rows
    def yfm = yf + 0.26 / rows
    def vf = 0.0676 - yfm * yfm
    def hf = Math.sqrt(vf < 0 ? 0 : vf)
    ai.addPart('#f6f3ea', -hf, cy + yf, -0.075, 2 * hf, 0.52 / rows + 0.002, 0.01)                 // face
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
