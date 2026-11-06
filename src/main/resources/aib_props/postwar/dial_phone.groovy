// 다이얼 전화기: a black rotary telephone with its handset on the cradle; on a table or chest top (y = 0)
def z = -0.5
def black = '#151515'
ai.addRoundBox(black, -0.12, 0, z - 0.11, 0.24, 0.08, 0.22, 0.04)          // base
ai.addEllipsoid(black, 0, 0.08, z + 0.02, 0.11, 0.04, 0.09)                // upper body
ai.addCylinder('#e8e2d2', 0, 0.075, z - 0.06, 0.055, 0.03)                 // dial face
for (int i = 0; i < 10; i++) {
    def a = Math.toRadians(i * 30 + 60)
    ai.addSphere(black, 0.04 * Math.cos(a), 0.107, z - 0.06 + 0.04 * Math.sin(a), 0.008)   // finger holes
}
ai.addCapsule(black, -0.12, 0.15, z + 0.02, 0.12, 0.15, z + 0.02, 0.022)   // handset
ai.addSphere(black, -0.12, 0.14, z + 0.02, 0.035)                          // ear and mouth cups
ai.addSphere(black, 0.12, 0.14, z + 0.02, 0.035)
ai.addTube(black, [[0.12, 0.02, z + 0.1], [0.2, 0.01, z + 0.15], [0.24, 0.0, z + 0.05]], 0.006)   // curly cord
