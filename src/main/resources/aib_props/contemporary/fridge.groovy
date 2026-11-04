// two-door fridge, 0.9 wide, 0.78 deep, 1.85 tall
def w = 0.9
def d = 0.78
def h = 1.85
ai.addRoundBox('#e9ebed', -w / 2, 0.06, -d, w, h - 0.06, d, 0.03)          // body
ai.addPart('#5d6166', -w / 2 + 0.03, 0, -d + 0.04, w - 0.06, 0.06, d - 0.08)  // kick plate
ai.addPart('#f4f5f6', -w / 2 + 0.015, 1.25, -d - 0.015, w - 0.03, h - 1.27, 0.03)  // freezer door
ai.addPart('#f4f5f6', -w / 2 + 0.015, 0.08, -d - 0.015, w - 0.03, 1.15, 0.03)      // fridge door
ai.addPart('#a3a9b0', -w / 2 + 0.015, 1.235, -d - 0.016, w - 0.03, 0.012, 0.031)   // gap between the doors
ai.addPart('#b8bec4', w / 2 - 0.1, 1.32, -d - 0.06, 0.035, 0.38, 0.035)            // freezer handle
ai.addPart('#b8bec4', w / 2 - 0.1, 0.62, -d - 0.06, 0.035, 0.52, 0.035)            // fridge handle
ai.addPart('#b8bec4', w / 2 - 0.1, 1.32, -d - 0.03, 0.035, 0.035, 0.03)            // handle feet
ai.addPart('#b8bec4', w / 2 - 0.1, 1.665, -d - 0.03, 0.035, 0.035, 0.03)
ai.addPart('#b8bec4', w / 2 - 0.1, 0.62, -d - 0.03, 0.035, 0.035, 0.03)
ai.addPart('#b8bec4', w / 2 - 0.1, 1.105, -d - 0.03, 0.035, 0.035, 0.03)
ai.addPart('#8a9097', -0.07, 1.72, -d - 0.017, 0.14, 0.025, 0.005)                // logo
ai.addPart('#3b6f9e', -w / 2 + 0.08, 1.12, -d - 0.017, 0.12, 0.08, 0.005)         // small display panel
