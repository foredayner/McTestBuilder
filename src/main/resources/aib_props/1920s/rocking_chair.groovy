// wooden rocking chair on curved runners, facing -Z, its back toward z = 0
def wood = '#7a4a26'
def dark = '#55321a'
def zc = -0.5
ai.mirrorOn()
// a runner: short pieces along a gentle curve under each side
for (int i = 0; i < 8; i++) {
    def t0 = -0.42 + i * 0.105
    def t1 = t0 + 0.105
    ai.addCapsule(dark, 0.24, 0.05 + 0.25 * t0 * t0, zc + t0, 0.24, 0.05 + 0.25 * t1 * t1, zc + t1, 0.022)
}
ai.addCylinder(wood, 0.22, 0.06, zc - 0.25, 0.022, 0.36)            // front legs
ai.addCylinder(wood, 0.22, 0.06, zc + 0.2, 0.022, 0.36)             // back legs
ai.addCapsule(wood, 0.24, 0.62, zc - 0.28, 0.24, 0.62, zc + 0.1, 0.022)   // arms
ai.addCylinder(wood, 0.24, 0.42, zc - 0.26, 0.016, 0.2)             // arm posts
ai.addCapsule(wood, 0.21, 0.42, zc + 0.22, 0.21, 1.12, zc + 0.36, 0.024) // back posts, leaning back
ai.mirrorOff()
ai.addRoundBox(wood, -0.24, 0.4, zc - 0.28, 0.48, 0.05, 0.5, 0.02)   // seat
ai.addRoundBox('#8c2f2f', -0.2, 0.45, zc - 0.25, 0.4, 0.04, 0.42, 0.02)   // cushion
// the back's spindles, leaning back like the posts
for (int i = 0; i < 5; i++) {
    def x = -0.14 + i * 0.07
    ai.addCapsule(wood, x, 0.47, zc + 0.24, x, 1.02, zc + 0.35, 0.011)
}
ai.addCapsule(wood, -0.21, 1.05, zc + 0.355, 0.21, 1.05, zc + 0.355, 0.022)   // top rail
