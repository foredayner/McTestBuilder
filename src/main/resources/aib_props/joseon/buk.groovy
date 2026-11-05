// 북: a barrel drum on a wooden stand, its painted head facing front (-Z), in the middle of its block
def z = -0.5
def y = 0.62
def wood = '#5a3a22'
ai.addEllipsoid('#8a2a1e', 0, y, z, 0.3, 0.3, 0.2)              // the barrel body
// the heads: round discs facing front and back, with the red-and-blue taegeuk in front
for (int j = 0; j < 12; j++) {
    def a = -0.24 + j * 0.04
    def am = a + 0.02
    def v = 0.0576 - am * am
    def h = Math.sqrt(v < 0 ? 0.0 : v)
    ai.addPart('#efe6d0', -h, y + a, z - 0.2, 2 * h, 0.041, 0.02)
    ai.addPart('#efe6d0', -h, y + a, z + 0.18, 2 * h, 0.041, 0.02)
    def vt = 0.0144 - am * am
    def ht = Math.sqrt(vt < 0 ? 0.0 : vt)
    if (ht > 0) {
        ai.addPart(am > 0 ? '#b8302a' : '#2a4a9a', -ht, y + a, z - 0.205, 2 * ht, 0.041, 0.006)
    }
}
// brass tacks around the front head
for (int i = 0; i < 12; i++) {
    def t = Math.toRadians(i * 30)
    ai.addSphere('#c9a24a', 0.25 * Math.sin(t), y + 0.25 * Math.cos(t), z - 0.19, 0.012)
}
// the stand: two posts with feet, and the beam the drum hangs from
ai.mirrorOn()
ai.addCylinder(wood, 0.36, 0, z, 0.03, 0.98)
ai.addCapsule(wood, 0.36, 0.03, z - 0.25, 0.36, 0.03, z + 0.25, 0.03)
ai.addCapsule(wood, 0.36, y, z, 0.29, y, z, 0.02)               // pegs into the drum
ai.mirrorOff()
ai.addCapsule(wood, -0.38, 0.98, z, 0.38, 0.98, z, 0.03)        // top beam
