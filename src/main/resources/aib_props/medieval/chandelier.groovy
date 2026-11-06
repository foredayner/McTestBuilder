// iron ring chandelier with candles, hanging on chains under the ceiling (y = 0 is the air block under it); it lights the room
def iron = '#2a2826'
def ring = 0.42
def h = 0.35
for (int i = 0; i < 12; i++) {                                  // the iron ring
    def a0 = Math.toRadians(i * 30)
    def a1 = Math.toRadians((i + 1) * 30)
    ai.addCapsule(iron, ring * Math.cos(a0), h, -0.5 + ring * Math.sin(a0), ring * Math.cos(a1), h, -0.5 + ring * Math.sin(a1), 0.02)
}
for (int i = 0; i < 3; i++) {                                   // chains up to a hook
    def a = Math.toRadians(i * 120 + 30)
    ai.addCapsule(iron, ring * Math.cos(a), h, -0.5 + ring * Math.sin(a), 0, 0.92, -0.5, 0.008)
}
ai.addCylinder(iron, 0, 0.92, -0.5, 0.02, 0.08)                 // hook
for (int i = 0; i < 6; i++) {                                   // candles on the ring
    def a = Math.toRadians(i * 60)
    def x = ring * Math.cos(a)
    def zz = -0.5 + ring * Math.sin(a)
    ai.addCylinder(iron, x, h + 0.01, zz, 0.035, 0.015)
    ai.addCylinder('#efe6cc', x, h + 0.025, zz, 0.022, 0.12)
    ai.addSphere('#ffc04a', x, h + 0.165, zz, 0.014)
}
