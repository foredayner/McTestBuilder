// window curtain, 2 wide and 2 high, hung on a rod against the wall (z = 0): two panels that meet at the
// top in the middle and sweep out to the bottom corners, with pleats; what is behind shows in the middle
def top = 1.9
def bottom = 0.02
def h = top - bottom
def cloth = '#dccdb0'
def fold = '#c2b291'
def ridge = '#ebe1cb'
// the rod with its brackets and end knobs
ai.addCapsule('#4a3b2c', -1.04, 1.96, -0.1, 1.04, 1.96, -0.1, 0.022)
ai.mirrorOn()
ai.addPart('#4a3b2c', 0.88, 1.94, -0.1, 0.03, 0.03, 0.1)
ai.addSphere('#4a3b2c', 1.05, 1.96, -0.1, 0.04)
// rings on the rod
for (int i = 0; i < 6; i++) {
    ai.addPart('#4a3b2c', -0.95 + i * 0.17, 1.915, -0.125, 0.02, 0.07, 0.05)
}
// the left panel (mirrored to the right): strips from the top, each ending at the sweeping inner edge
int n = 18
for (int k = 0; k < n; k++) {
    def f = (k + 0.5) / n
    def inner = -Math.pow(f, 2.5)
    def y = top - (k + 1) * h / n
    def width = inner + 0.99
    if (width > 0.03) {
        ai.addPart(cloth, -0.99, y, -0.11, width, h / n + 0.002, 0.04)
    }
}
// pleats: a shadow fold and a light ridge, from the top down to where the inner edge crosses them
for (int i = 0; i < 6; i++) {
    def x = -0.9 + i * 0.15
    def reach = Math.pow(Math.abs(x), 0.4) * h
    ai.addPart(fold, x - 0.012, top - reach, -0.118, 0.024, reach, 0.01)
    if (i < 5) {
        def xr = x + 0.075
        def reachR = Math.pow(Math.abs(xr), 0.4) * h
        ai.addPart(ridge, xr - 0.015, top - reachR, -0.122, 0.03, reachR, 0.012)
    }
}
// a heading band along the top of each panel
ai.addPart(ridge, -0.99, top - 0.06, -0.124, 0.99, 0.06, 0.016)
ai.mirrorOff()
