// heraldic heater shield with two crossed swords behind it, hung on the wall (z = 0) about 1.6 up
def y = 1.25
def steel = '#b8bcc0'
ai.addPart(steel, -0.42, y + 0.1, -0.03, 0.84, 0.03, 0.01, 0, 0, 35)   // crossed sword blades
ai.addPart(steel, -0.42, y + 0.1, -0.03, 0.84, 0.03, 0.01, 0, 0, -35)
// the shield: rows narrowing to a point at the bottom, in two colours
for (int j = 0; j < 12; j++) {
    def t = j / 11.0
    def w = j < 5 ? 0.3 : 0.3 * Math.sqrt(1 - ((j - 4) / 8.0) * ((j - 4) / 8.0))
    def yy = y + 0.62 - j * 0.05
    ai.addPart('#2a4a8a', -w, yy, -0.06, w, 0.052, 0.025)
    ai.addPart('#b8302a', 0, yy, -0.06, w, 0.052, 0.025)
}
ai.addPart('#e8c84a', -0.02, y + 0.07, -0.066, 0.04, 0.6, 0.006)       // gold pale down the middle
ai.addSphere('#e8c84a', 0, y + 0.4, -0.07, 0.04)                       // and a boss
