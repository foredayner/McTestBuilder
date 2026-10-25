// Large anime/game-style combat scythe, about 2.5 tall with a 1.8-long crescent blade.
// Parts:
//  - Shaft: dark wood pole from the bottom (y -0.2) up to the collar (y 2.4)
//  - Grip wrap at the hand (y 0 to 0.45), black leather
//  - Gold collar and iron butt cap
//  - Crescent blade: centre (0.5, 1.8), radius 0.7, sweeping from the top of the shaft
//    out to +X and down to a point; flat in Z, tapering from 0.28 wide to a point
//  - Bright iron cutting edge on the inner (lower) side, dark steel spine on the back

// Shaft
ai.addCapsule('spruce_planks', 0, -0.2, 0, 0, 2.4, 0, 0.032)

// Grip wrap around the hand
ai.addCapsule('black_concrete', 0, 0.0, 0, 0, 0.45, 0, 0.045)

// Butt cap at the bottom
ai.addSphere('iron_block', 0, -0.2, 0, 0.045)

// Gold collar and ring where the blade socket sits
ai.addCylinder('gold_block', 0, 2.28, 0, 0.045, 0.06)
ai.addSphere('gold_block', 0, 2.42, 0, 0.06)
ai.addEllipsoid('iron_block', 0.05, 2.42, 0, 0.09, 0.09, 0.05)

// Crescent blade parameters
double cx = 0.5
double cy = 1.8
double R = 0.7
int N = 12

// Blade body: overlapping flat ellipsoids along the arc, tapering toward the tip
def inner = []
def outer = []
for (int i = 0; i <= N; i++) {
    double t = i / (double) N
    double thDeg = 125 - 150 * t
    double th = Math.toRadians(thDeg)
    double c = Math.cos(th)
    double s = Math.sin(th)
    double w = 0.14 - 0.11 * t

    double x = cx + R * c
    double y = cy + R * s
    ai.addEllipsoid('light_gray_concrete', x, y, 0, w, w, 0.035)

    // Inner (cutting) edge and outer (back) spine points
    inner << [cx + (R - 0.8 * w) * c, cy + (R - 0.8 * w) * s, 0.0]
    outer << [cx + (R + 0.8 * w) * c, cy + (R + 0.8 * w) * s, 0.0]
}

// Bright cutting edge on the inner side
ai.addTube('iron_block', inner, 0.022)

// Darker steel spine on the back
ai.addTube('gray_concrete', outer, 0.02)

// Blade tip accent
ai.addSphere('iron_block', cx + R * Math.cos(Math.toRadians(-25)), cy + R * Math.sin(Math.toRadians(-25)), 0, 0.035)