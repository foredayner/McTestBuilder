// bar stool: a round padded seat on a chrome post with a foot ring, in the middle of its block
def z = -0.5
def chrome = '#c8cacc'
ai.addCylinder(chrome, 0, 0, z, 0.2, 0.025)                    // round base
ai.addCylinder(chrome, 0, 0.025, z, 0.03, 0.66)                // post
ai.addCylinder('#3a3a3a', 0, 0.62, z, 0.06, 0.04)              // gas lift collar
ai.addCylinder('#2a2a2a', 0, 0.68, z, 0.2, 0.02)               // seat plate
ai.addEllipsoid('#7a2a22', 0, 0.72, z, 0.2, 0.05, 0.2)         // padded seat
for (int i = 0; i < 8; i++) {                                  // foot ring
    def a0 = Math.toRadians(i * 45)
    def a1 = Math.toRadians((i + 1) * 45)
    ai.addCapsule(chrome, 0.15 * Math.cos(a0), 0.3, z + 0.15 * Math.sin(a0), 0.15 * Math.cos(a1), 0.3, z + 0.15 * Math.sin(a1), 0.01)
}
ai.mirrorOn()
ai.addCapsule(chrome, 0.03, 0.3, z, 0.15, 0.3, z, 0.008)        // ring spokes
ai.mirrorOff()
