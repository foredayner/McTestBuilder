// 소반: a small round dining table on the floor with rice, soup and side dishes, in the middle of its block
def z = -0.5
def wood = '#6b3a1c'
ai.addCylinder(wood, 0, 0.26, z, 0.32, 0.04)                    // round top
ai.addCylinder('#55301a', 0, 0.245, z, 0.3, 0.015)              // apron under it
for (int i = 0; i < 4; i++) {
    def a = Math.toRadians(45 + i * 90)
    ai.addCapsule(wood, 0.2 * Math.cos(a), 0.24, z + 0.2 * Math.sin(a), 0.24 * Math.cos(a), 0.02, z + 0.24 * Math.sin(a), 0.022)   // curved legs
}
ai.addCylinder('#e8e2d2', -0.1, 0.3, z - 0.08, 0.06, 0.05)       // rice bowl
ai.addEllipsoid('#f8f6ee', -0.1, 0.35, z - 0.08, 0.055, 0.025, 0.055)   // the rice
ai.addCylinder('#c9a24a', 0.08, 0.3, z - 0.08, 0.065, 0.045)      // brass soup bowl
ai.addCylinder('#b5562a', 0.08, 0.335, z - 0.08, 0.055, 0.006)    // the soup
ai.addCylinder('#e8e2d2', -0.14, 0.3, z + 0.12, 0.045, 0.02)      // side dishes
ai.addEllipsoid('#c2452e', -0.14, 0.32, z + 0.12, 0.035, 0.012, 0.035)   // kimchi
ai.addCylinder('#e8e2d2', 0.02, 0.3, z + 0.14, 0.045, 0.02)
ai.addEllipsoid('#5f8a3a', 0.02, 0.32, z + 0.14, 0.035, 0.012, 0.035)    // greens
ai.addCylinder('#e8e2d2', 0.17, 0.3, z + 0.1, 0.04, 0.02)
ai.addEllipsoid('#d9b46a', 0.17, 0.32, z + 0.1, 0.03, 0.012, 0.03)
ai.addPart('#c9a24a', 0.2, 0.302, z - 0.2, 0.012, 0.006, 0.2, 10, 0, 0)  // brass spoon
ai.addPart('#c9a24a', 0.23, 0.302, z - 0.2, 0.006, 0.006, 0.22, 10, 0, 0) // chopsticks
ai.addPart('#c9a24a', 0.245, 0.302, z - 0.2, 0.006, 0.006, 0.22, 10, 0, 0)
