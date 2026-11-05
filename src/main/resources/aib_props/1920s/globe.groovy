// floor-standing globe on a wooden tripod, about 1.1 tall, in the middle of its block
def z = -0.5
def wood = '#6b3a1c'
def brass = '#c9a24a'
ai.addCylinder(wood, 0, 0.42, z, 0.05, 0.08)                         // hub
ai.addCylinder(wood, 0, 0.5, z, 0.025, 0.12)                         // post
for (int i = 0; i < 3; i++) {
    def a = Math.toRadians(i * 120 + 90)
    ai.addCapsule(wood, 0, 0.44, z, 0.3 * Math.cos(a), 0.02, z + 0.3 * Math.sin(a), 0.022, 0.016)   // legs
}
ai.addSphere('#2f6aa3', 0, 0.86, z, 0.24)                            // the globe: sea...
ai.addEllipsoid('#5f8f4a', 0.08, 0.93, z - 0.12, 0.12, 0.09, 0.12)  // ...and lands
ai.addEllipsoid('#5f8f4a', -0.12, 0.8, z - 0.1, 0.1, 0.12, 0.1)
ai.addEllipsoid('#c9b07a', 0.12, 0.78, z + 0.08, 0.1, 0.07, 0.11)
ai.addEllipsoid('#5f8f4a', -0.05, 1.0, z + 0.1, 0.13, 0.06, 0.1)
// the brass half-meridian around it, from below to above
for (int i = 0; i < 10; i++) {
    def a0 = Math.toRadians(-90 + i * 18)
    def a1 = Math.toRadians(-90 + (i + 1) * 18)
    ai.addCapsule(brass, 0.27 * Math.cos(a0), 0.86 + 0.27 * Math.sin(a0), z, 0.27 * Math.cos(a1), 0.86 + 0.27 * Math.sin(a1), z, 0.012)
}
