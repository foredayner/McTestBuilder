// 장구: an hourglass drum lying along x on the floor, in the middle of its block; red body, leather heads, red ropes
def z = -0.5
def y = 0.2
def red = '#9e2a22'
def skin = '#efe6d0'
// the body: thin round slices along x, narrow in the waist and wide at the ends
int n = 21
for (int i = 0; i < n; i++) {
    def x = -0.3 + i * 0.03
    def t = x / 0.3
    def r = 0.04 + 0.12 * t * t
    ai.addEllipsoid(red, x, y, z, 0.03, r, r)
}
// the leather heads: round discs facing along x
ai.mirrorOn()
for (int j = 0; j < 10; j++) {
    def a = -0.18 + j * 0.036
    def am = a + 0.018
    def v = 0.0324 - am * am
    def h = Math.sqrt(v < 0 ? 0.0 : v)
    ai.addPart(skin, 0.33, y + a, z - h, 0.025, 0.037, 2 * h)
}
// the red ropes from one head to the other, over the top and the front
for (int k = 0; k < 4; k++) {
    def t = Math.toRadians(-60 + k * 40)
    ai.addCapsule('#c23a2e', 0.32, y + 0.17 * Math.cos(t), z - 0.17 * Math.sin(t), 0.02, y + 0.06 * Math.cos(t), z - 0.06 * Math.sin(t), 0.006)
}
ai.mirrorOff()
ai.addRoundBox('#c9b07a', -0.38, 0, z - 0.2, 0.76, 0.02, 0.4, 0.01)   // a straw mat under it
