// 화로: a brass brazier of glowing charcoal on three little feet, in the middle of its block; it gives a little light
def z = -0.5
def brass = '#a8823a'
for (int i = 0; i < 3; i++) {
    def a = Math.toRadians(90 + i * 120)
    ai.addCylinder(brass, 0.15 * Math.cos(a), 0, z + 0.15 * Math.sin(a), 0.03, 0.05)   // feet
}
ai.addCone(brass, 0, 0.05, z, 0.17, 0.25, 0.22)                 // the bowl, wider at the top
ai.addCylinder('#8a6a2a', 0, 0.26, z, 0.26, 0.02)               // rim
ai.addEllipsoid('#8a8a84', 0, 0.255, z, 0.22, 0.03, 0.22)       // ash
for (int i = 0; i < 6; i++) {
    def a = Math.toRadians(i * 60 + 20)
    ai.addSphere(i % 2 == 0 ? '#e0602a' : '#ff9a3a', 0.1 * Math.cos(a), 0.28, z + 0.1 * Math.sin(a), 0.03)   // glowing charcoal
}
ai.addSphere('#ffb44a', 0, 0.29, z, 0.035)
ai.addCapsule('#3a3a3a', 0.05, 0.3, z - 0.1, 0.32, 0.18, z - 0.16, 0.008)   // fire tongs leaning on the rim
ai.addCapsule('#3a3a3a', 0.06, 0.3, z - 0.08, 0.33, 0.18, z - 0.14, 0.008)
