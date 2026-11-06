// wooden spinning wheel, in the middle of its block
def z = -0.5
def wood = '#7a4a26'
ai.addRoundBox(wood, -0.38, 0.32, z - 0.08, 0.76, 0.05, 0.16, 0.02)    // bench
ai.addCapsule(wood, -0.32, 0.32, z - 0.06, -0.36, 0.0, z - 0.14, 0.018)  // legs
ai.addCapsule(wood, -0.32, 0.32, z + 0.06, -0.36, 0.0, z + 0.14, 0.018)
ai.addCapsule(wood, 0.32, 0.32, z, 0.36, 0.0, z, 0.018)
// the big wheel standing on the left end, spokes and rim in the x-y plane
def cx = -0.12
def cy = 0.72
def r = 0.3
for (int i = 0; i < 16; i++) {
    def a0 = Math.toRadians(i * 22.5)
    def a1 = Math.toRadians((i + 1) * 22.5)
    ai.addCapsule(wood, cx + r * Math.cos(a0), cy + r * Math.sin(a0), z, cx + r * Math.cos(a1), cy + r * Math.sin(a1), z, 0.015)
}
for (int i = 0; i < 8; i++) {
    def a = Math.toRadians(i * 45)
    ai.addCapsule(wood, cx, cy, z, cx + r * Math.cos(a), cy + r * Math.sin(a), z, 0.007)
}
ai.addSphere(wood, cx, cy, z, 0.03)                                       // hub
ai.addCapsule(wood, cx, 0.37, z - 0.04, cx, cy, z - 0.04, 0.016)          // wheel posts
ai.addCapsule(wood, cx, 0.37, z + 0.04, cx, cy, z + 0.04, 0.016)
ai.addCapsule(wood, 0.24, 0.37, z, 0.24, 0.66, z, 0.016)                  // spindle post
ai.addCapsule('#d8c49a', 0.2, 0.62, z, 0.34, 0.66, z, 0.018)              // spun wool on the spindle
ai.addEllipsoid('#efe6d0', 0.32, 0.47, z + 0.12, 0.08, 0.06, 0.06)        // a basket's worth of fleece
