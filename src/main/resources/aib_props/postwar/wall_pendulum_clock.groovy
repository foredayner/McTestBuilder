// 벽 괘종시계: a wooden pendulum wall clock with a round face and a glass case, about 1.7 up on the wall (z = 0)
def y = 1.1
def wood = '#5a3018'
ai.addRoundBox(wood, -0.18, y, -0.12, 0.36, 0.8, 0.12, 0.02)        // case
ai.addPart('#9fb8c0/25', -0.12, y + 0.06, -0.125, 0.24, 0.42, 0.006)   // glass over the pendulum
ai.addPart('#c9a24a', -0.006, y + 0.2, -0.08, 0.012, 0.32, 0.006)   // pendulum rod
ai.addEllipsoid('#c9a24a', 0, y + 0.18, -0.08, 0.05, 0.05, 0.008)  // bob
ai.addRoundBox(wood, -0.2, y + 0.78, -0.13, 0.4, 0.06, 0.13, 0.02) // top moulding
// the face: a round dial of rows
def cy = y + 0.64
for (int j = 0; j < 8; j++) {
    def a = -0.12 + j * 0.03
    def am = a + 0.015
    def v = 0.0144 - am * am
    def h = Math.sqrt(v < 0 ? 0.0 : v)
    ai.addPart('#f2ead3', -h, cy + a, -0.125, 2 * h, 0.032, 0.006)
}
for (int i = 0; i < 12; i++) {
    def t = Math.toRadians(i * 30)
    ai.addPart('#2a2a2a', 0.095 * Math.sin(t) - 0.005, cy + 0.095 * Math.cos(t) - 0.012, -0.13, 0.01, 0.024, 0.003, 0, 0, -i * 30)
}
ai.addPart('#1a1a1a', -0.005, cy - 0.005, -0.133, 0.01, 0.07, 0.003, 0, 0, 30)    // hands
ai.addPart('#1a1a1a', -0.004, cy - 0.005, -0.134, 0.008, 0.09, 0.003, 0, 0, -100)
