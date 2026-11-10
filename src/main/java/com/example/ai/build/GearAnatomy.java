package com.example.ai.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What well-known hand-held things are made of, so a request that names one gets its
 * recognisable parts (a katana's wrapped grip and round guard, a rapier's swept hilt...). The
 * request's words pick the entries; each is added to the equipment prompt as a short guide.
 */
public final class GearAnatomy {

    private GearAnatomy() {
    }

    /** Words that name it (Korean and English, lower case) and what it is made of. */
    public record Entry(List<String> words, String parts) {
    }

    public static final List<Entry> ENTRIES = List.of(
        e("katana: a long grip (0.3-0.35) wrapped in a criss-cross diamond pattern (dark cord over a lighter base: small "
            + "boxes turned 45 degrees), a round or rounded-square guard (tsuba) 0.12-0.16 across, a short collar (habaki), "
            + "then a slim single-edged blade 0.06-0.09 wide that CURVES gently backward along its length (a chain of 4-6 "
            + "boxes each turned a few degrees more), with a light edge line (hamon) along the cutting side and a sharp "
            + "angled tip", "카타나", "일본도", "katana", "태도", "tachi"),
        e("Korean hwando (환도): a slim, slightly curved single-edged blade, a simple oval or round guard, a wooden grip "
            + "wrapped in cord, a scabbard ring", "환도", "hwando", "조선검"),
        e("rapier: a very thin straight blade (0.03-0.05 wide) 1.0-1.2 long, a swept hilt of thin curving bars round the "
            + "hand (a cup or several rings), a long cross guard and a round pommel", "레이피어", "rapier", "세검", "펜싱검"),
        e("longsword or knight sword: a straight double-edged blade with a central groove (fuller), a wide straight "
            + "cross guard, a grip for one and a half hands, a round or disc pommel", "롱소드", "장검", "longsword", "기사검",
            "knight sword", "양날검"),
        e("greatsword / claymore / zweihander: a huge straight blade (1.4-2.4) 0.25-0.5 wide, a long two-handed grip "
            + "(0.5-0.7), a wide guard (often with side rings or spikes), a heavy pommel", "대검", "그레이트소드", "greatsword",
            "claymore", "클레이모어", "zweihander", "츠바이핸더", "양손검"),
        e("dagger or knife: a short blade 0.25-0.4 long, pointed, a small guard or none, a short grip", "단검", "단도",
            "dagger", "knife", "나이프", "칼날 짧은"),
        e("scimitar / sabre / cutlass: a single-edged blade that widens and curves strongly toward the tip (a chain of "
            + "turned boxes), a knuckle guard bar from the guard to the pommel", "시미터", "세이버", "사브르", "scimitar",
            "sabre", "saber", "커틀러스", "cutlass", "곡도", "만도"),
        e("guandao / glaive (언월도): a long pole (1.8-2.6), at its top a broad curved single-edged blade 0.5-0.8 long and "
            + "0.15-0.3 wide with a notch or spike on its back, often a dragon head or ring where blade meets pole, a "
            + "pointed butt cap at the bottom", "언월도", "월도", "청룡언월도", "guandao", "glaive", "글레이브", "관도"),
        e("halberd / ji (방천화극): a long pole with an axe blade on one side, a spike or hook on the other and a spear "
            + "point on top; 방천화극 has a spear point with a crescent blade on one or both sides", "할버드", "halberd",
            "방천화극", "화극", "극", "ji"),
        e("trident / 삼지창: a long pole with three prongs on top (the middle one longer), barbs at the tips",
            "삼지창", "trident", "트라이던트"),
        e("spear / lance: a long pole, a leaf-shaped or pointed head, a socket collar, maybe a tassel or banner under the "
            + "head; a lance has a big cone-shaped hand guard", "창", "spear", "lance", "랜스", "장창"),
        e("scythe / 낫: a long shaft, at its top a big crescent blade sweeping out to one side and curving down to a point "
            + "(a chain of 5-8 flat boxes, each turned a little more, wide near the shaft and narrowing to the tip; a bright "
            + "edge on the inner side); a small hand-sickle is the same on a short handle", "낫", "scythe", "사이드", "대낫",
            "sickle", "sicle"),
        e("mace / morning star / 철퇴: a handle with a heavy head on top: flanged ribs round a core, or a ball with spikes "
            + "(spikes are small cones or turned boxes)", "철퇴", "메이스", "mace", "모닝스타", "morning star", "철곤"),
        e("flail / 도리깨: a handle, a chain of a few links (small turned boxes), and a spiked ball or a wooden beater "
            + "hanging from it", "도리깨", "flail", "플레일"),
        e("war hammer / maul: a handle with a big block head across it, faces at both ends, often a spike on the back",
            "워해머", "전투망치", "war hammer", "warhammer", "maul", "몰", "해머"),
        e("battle axe / double-bit axe (쌍날도끼): a handle with a head that has a crescent blade on BOTH sides",
            "쌍날도끼", "양날도끼", "double axe", "battle axe", "배틀액스", "전투도끼", "라브리스"),
        e("club / bat / 몽둥이: a handle widening into a thick end; a baseball bat is smooth and round at the end, a "
            + "spiked club has nails or spikes", "몽둥이", "곤봉", "야구방망이", "배트", "bat", "club"),
        e("staff / quarterstaff / 봉: a long even pole with metal bands at both ends", "봉", "곤", "quarterstaff",
            "육척봉", "여의봉"),
        e("magic staff / wand: a shaft with an ornament at the top holding a gem or orb (a gem: a few boxes turned 45 "
            + "degrees, or one smooth sphere in a colour), prongs or a ring round it, runes or bands down the shaft; a "
            + "wand is short (0.4-0.6)", "마법 지팡이", "마법지팡이", "스태프", "magic staff", "staff", "wand", "완드",
            "요술봉", "마법봉"),
        e("umbrella: a straight shaft, a curved J handle at the bottom, a canopy of 8 panels at the top (closed: a slim "
            + "cone of folds; open: a wide shallow dome made of turned panels)", "우산", "양산", "umbrella", "parasol"),
        e("flag / banner: a pole with a cloth rectangle or swallowtail hanging from the top (a thin box, maybe with an "
            + "emblem on it), a finial on top", "깃발", "깃대", "flag", "banner", "배너", "군기"),
        e("guitar / instrument: a body (two rounded bumps for a guitar), a long neck with frets and strings, a headstock "
            + "with tuning pegs", "기타", "guitar", "악기", "바이올린", "violin", "베이스", "ukulele", "우쿨렐레"),
        e("frying pan / kitchen tool: a round flat pan with a raised rim and a long handle", "프라이팬", "후라이팬",
            "frying pan", "냄비", "국자", "뒤집개", "spatula"),
        e("fishing rod: a long thin rod tapering to the tip, a reel near the grip, a line and a hook or float at the end",
            "낚싯대", "낚시대", "fishing rod"),
        e("chainsaw / power tool: a motor body with a handle on top and a grip at the back, a long flat bar with a chain "
            + "of small teeth round its edge", "전기톱", "체인소", "chainsaw", "드릴", "drill"),
        e("lightsaber / energy blade: a metal hilt (a cylinder with grooves, a switch, an emitter at the top) and a "
            + "straight glowing blade of a see-through colour with a bright core", "광선검", "라이트세이버", "lightsaber",
            "빔소드", "beam sword", "에너지 검"),
        e("gun (pistol, rifle, shotgun): a barrel along -X with a muzzle, a body with a trigger and trigger guard, a grip "
            + "under the hand (a pistol) or a stock behind (a rifle), sights on top, a magazine", "총", "권총", "소총",
            "라이플", "샷건", "pistol", "rifle", "shotgun", "gun", "기관총"),
        e("crossbow: a stock lying along X, a short bow across it at the front (limbs left and right), a string pulled "
            + "back, a trigger under it", "석궁", "crossbow", "쇠뇌"),
        e("traditional Korean bow (각궁): short and strongly curved, the tips curling back the other way",
            "각궁", "국궁", "korean bow"),
        e("pickaxe: a handle with a head across it, two long points curving down, a socket in the middle", "곡괭이",
            "pickaxe", "피켈"),
        e("shovel: a handle with a D or T grip at the top end, a flat or pointed blade at the other", "삽", "shovel",
            "spade", "모종삽")
    );

    private static Entry e(String parts, String... words) {
        List<String> list = new ArrayList<>();
        for (String w : words) {
            list.add(w.toLowerCase(Locale.ROOT));
        }
        return new Entry(list, parts);
    }

    /** The guides for what the request names, at most four (the longest matching word first). */
    public static List<String> forRequest(String request) {
        List<String> out = new ArrayList<>();
        if (request == null || request.isBlank()) {
            return out;
        }
        String r = request.toLowerCase(Locale.ROOT);
        List<Object[]> hits = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            int best = 0;
            for (String w : entry.words()) {
                if (matches(r, w)) {
                    best = Math.max(best, w.length());
                }
            }
            if (best > 0) {
                hits.add(new Object[] { best, entry.parts() });
            }
        }
        hits.sort((a, b) -> (Integer) b[0] - (Integer) a[0]);
        for (int i = 0; i < Math.min(4, hits.size()); i++) {
            out.add((String) hits.get(i)[1]);
        }
        return out;
    }

    /** A word in the request: English words whole; short Korean words (one or two letters) only on their own. */
    static boolean matches(String request, String word) {
        if (word.chars().allMatch(c -> c < 128)) {
            return java.util.regex.Pattern.compile("(^|[^a-z])" + java.util.regex.Pattern.quote(word) + "s?($|[^a-z])").matcher(request).find();
        }
        if (word.length() <= 2) {
            // 창, 낫, 봉, 극, 삽, 총: not inside other words (창문, 낫다, 봉투, 극장...), but with a particle after is fine
            return java.util.regex.Pattern.compile("(^|[^가-힣])" + java.util.regex.Pattern.quote(word)
                + "($|[^가-힣]|[을를이가은는의과와에으로도만])").matcher(request).find();
        }
        return request.contains(word);
    }
}
