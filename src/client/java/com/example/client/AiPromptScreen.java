package com.example.client;

import com.example.ai.build.BuildRecord;
import com.example.client.WoodUi.WoodButton;
import com.example.client.network.AiClientNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.example.client.WoodUi.*;

/**
 * Main window in a cozy farm-game style: a wooden frame around a parchment page, item tabs on
 * top, a red close button, a title plaque and speech-bubble chat.
 *   공방 (book)    - talk to the AI; finished designs go straight to 보관함 (cleared on every open)
 *   보관함 (chest) - saved builds and objects with previews; open one to take it as an item
 *   일지 (paper)   - saved logs by day
 *   설정 (gear)    - settings
 */
public class AiPromptScreen extends Screen {

    private enum Page { CREATE, HISTORY, LOGS }

    private static final int LINE = 10;
    private static final int ROW = 38;
    private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    // Kept across re-inits (the screen rebuilds its widgets when state changes).
    private static Page page = Page.CREATE;
    private static String selectedId = null;
    /** Size for placing a saved object: index into SCALES, kept while the game runs. */
    private static final int[] SCALES = { 50, 100, 200, 300, 500 };
    private static int scaleIndex = 1;
    private static final int BUILDS = 1;
    private static final int OBJECTS = 2;
    private static final int NEARBY = 3;  // objects placed around me
    private static final int GEAR = 4;
    private static int filter = BUILDS;
    private static int chatScroll = 0;    // pixels up from the bottom
    private static int listScroll = 0;    // rows
    private static int detailScroll = 0;  // pixels
    private static int fileScroll = 0;    // lines up from the bottom
    private static int dayIndex = 0;

    private record Tab(Page page, Item icon, String name) {
    }

    private static final Tab[] TABS = {
        new Tab(Page.CREATE, Items.WRITABLE_BOOK, "AI 공방"),
        new Tab(Page.HISTORY, Items.CHEST, "보관함"),
        new Tab(Page.LOGS, Items.PAPER, "작업 일지"),
        new Tab(null, Items.COMPARATOR, "설정")
    };

    private record IconAt(ItemStack stack, int x, int y) {
    }

    private final List<IconAt> icons = new ArrayList<>();
    private TextFieldWidget promptField;
    private String draftPrompt = "";
    private boolean confirmDelete = false;
    private int x0;
    private int y0;
    private int w;
    private int h;
    private List<String> fileLines = List.of();
    private List<String> days = List.of();

    public AiPromptScreen() {
        super(Text.literal("AI Builder"));
    }

    /** Open on a page: "CREATE", "HISTORY" (optionally with a record open) or "LOGS". */
    public static AiPromptScreen open(String pageName, String recordId) {
        page = Page.valueOf(pageName);
        selectedId = recordId;
        chatScroll = 0;
        listScroll = 0;
        detailScroll = 0;
        return new AiPromptScreen();
    }

    /** Open 보관함 on the 장비 tab (optionally with one open). */
    public static AiPromptScreen openGear(String recordId) {
        filter = GEAR;
        return open("HISTORY", recordId);
    }

    /** Open 보관함 on the "주변 배치" view (asks the server for the list). */
    public static AiPromptScreen openNearby() {
        filter = NEARBY;
        AiClientNetworking.requestNearby();
        return open("HISTORY", null);
    }

    public void onStateUpdated(boolean rebuildWidgets) {
        if (rebuildWidgets) {
            rebuild();
        }
    }

    private void rebuild() {
        if (this.promptField != null) {
            this.draftPrompt = this.promptField.getText();
        }
        saveEffects();
        this.clearChildren();
        this.init();
    }

    @Override
    protected void init() {
        super.init();
        AiClientState.setOpenScreen(this);
        layout();
        this.promptField = null;
        this.icons.clear();
        this.addDrawableChild(new WoodButton(this.x0 + this.w - 13, this.y0 - 7, 18, 18, Text.literal("×"), b -> this.close(), true));
        switch (page) {
            case CREATE -> buildCreate();
            case HISTORY -> buildHistory();
            case LOGS -> buildLogs();
        }
    }

    @Override
    public void removed() {
        AiClientState.clearOpenScreen(this);
        super.removed();
    }

    private void layout() {
        this.w = Math.min(this.width - 24, 500);
        this.h = Math.min(this.height - 44, 300);
        this.x0 = (this.width - this.w) / 2;
        this.y0 = (this.height - this.h) / 2 + 12;
    }

    // Page geometry (inside the parchment).
    private int left() { return this.x0 + 14; }
    private int right() { return this.x0 + this.w - 14; }
    private int top() { return this.y0 + 18; }
    private int bottom() { return this.y0 + this.h - 13; }

    private WoodButton button(String label, int x, int y, int width, ButtonWidget.PressAction action) {
        return this.addDrawableChild(new WoodButton(x, y, width, 18, Text.literal(label), action, false));
    }

    private void switchPage(Page target) {
        page = target;
        if (target == Page.HISTORY && filter == NEARBY) {
            AiClientNetworking.requestNearby();
        }
        this.confirmDelete = false;
        if (target == Page.LOGS) {
            fileScroll = 0;
        }
        rebuild();
    }

    // ---------------------------------------------------------------- AI 공방

    private int chatTop() { return top() + 22; }
    private int chatBottom() {
        return AiClientState.isWebSearchAvailable() || AiClientState.isGearMode() ? bottom() - 48 : bottom() - 26;
    }

    /** Equipment order: effect fields (empty = no such effect). */
    private TextFieldWidget hitField;
    private TextFieldWidget idleField;

    private void saveEffects() {
        if (this.idleField != null) {
            AiClientState.setEffects(this.hitField == null ? "" : this.hitField.getText(), this.idleField.getText());
        }
    }

    private TextFieldWidget effectField(int x, int y, int width, String placeholder, String text) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x + 5, y + 5, width - 10, 12, Text.literal(""));
        field.setDrawsBackground(false);
        field.setEditableColor(PAPER);
        field.setMaxLength(45);
        field.setPlaceholder(Text.literal(placeholder));
        field.setText(text);
        this.addSelectableChild(field);
        return field;
    }

    private void buildCreate() {
        boolean object = AiClientState.isObjectMode();
        boolean gear = AiClientState.isGearMode();
        button(gear ? "모드: 장비" : object ? "모드: 소품" : "모드: 건물", right() - 84, top(), 84, b -> AiClientState.cycleMode());
        if (gear) {
            // Choose the kind of equipment first, then describe it.
            com.example.content.GearKind kind = AiClientState.getGearKind();
            button("   종류: " + kind.korean, right() - 84 - 92, top(), 88, b -> AiClientState.cycleGearKind());
            this.icons.add(new IconAt(new ItemStack(kind.icon), right() - 84 - 89, top() + 1));
            if (kind == com.example.content.GearKind.SWORD) {
                // Swords only: how the attack swing looks.
                button("모션: " + com.example.content.GearMotions.korean(AiClientState.getMotion()), right() - 84 - 92 - 96, top(), 92,
                    b -> AiClientState.cycleMotion());
            }
        }

        int y = bottom() - 42;
        this.hitField = null;
        this.idleField = null;
        if (gear) {
            // Effect fields: left empty, the equipment has no such effect.
            int end = AiClientState.isWebSearchAvailable() ? right() - 80 : right();
            int span = end - left();
            if (AiClientState.getGearKind().worn()) {
                this.idleField = effectField(left(), y, span, "착용 시 이펙트·애니메이션 (비우면 없음)", AiClientState.getFxIdle());
            } else {
                int half = (span - 4) / 2;
                boolean bow = AiClientState.getGearKind() == com.example.content.GearKind.BOW;
                this.hitField = effectField(left(), y, half, bow ? "화살 이펙트·모양 (비우면 없음)" : "공격 시 이펙트 (비우면 없음)",
                    AiClientState.getFxHit());
                this.idleField = effectField(left() + half + 4, y, span - half - 4, "대기 시 이펙트·애니메이션 (비우면 없음)",
                    AiClientState.getFxIdle());
            }
        }
        if (AiClientState.isWebSearchAvailable()) {
            button(AiClientState.willUseWebSearch() ? "웹 검색: 켬" : "웹 검색: 끔", right() - 76, y, 76, b -> {
                AiClientState.toggleSessionWebSearch();
                rebuild();
            });
        }

        int fy = bottom() - 20;
        this.promptField = new TextFieldWidget(this.textRenderer, left() + 5, fy + 6, right() - left() - 78, 12, Text.literal(""));
        this.promptField.setDrawsBackground(false);
        this.promptField.setEditableColor(PAPER);
        // The chat command that carries the order is limited to 256 characters.
        this.promptField.setMaxLength(gear ? 150 : 220);
        this.promptField.setPlaceholder(Text.literal(gear ? "어떤 " + AiClientState.getGearKind().korean + "인지 적어 주세요…"
            : object ? "만들 소품을 적어 주세요…" : "지을 건물을 적어 주세요…"));
        this.promptField.setText(this.draftPrompt);
        this.promptField.setFocused(true);
        this.addSelectableChild(this.promptField);
        this.setFocused(this.promptField);
        button("   보내기", right() - 64, fy, 64, b -> sendBuild());
        this.icons.add(new IconAt(new ItemStack(Items.FEATHER), right() - 61, fy + 1));
    }

    private void sendBuild() {
        if (this.promptField == null) {
            return;
        }
        String prompt = this.promptField.getText().trim();
        if (prompt.isEmpty()) {
            return;
        }
        chatScroll = 0;
        AiClientState.addLog("[Request] " + prompt);
        if (AiClientState.willUseWebSearch()) {
            AiClientState.addLog("[Web] Search enabled for this request");
        }
        String what = AiClientState.isGearMode() ? AiClientState.getGearKind().korean
            : AiClientState.isObjectMode() ? "소품" : "건물";
        if (AiClientState.isGearMode()) {
            saveEffects();
            String hit = AiClientState.getGearKind().worn() ? "" : AiClientState.getFxHit().trim();
            String idle = AiClientState.getFxIdle().trim();
            if (!hit.isEmpty()) {
                AiClientState.addLog("[Request] " + (AiClientState.getGearKind() == com.example.content.GearKind.BOW ? "화살: " : "공격 시: ") + hit);
            }
            if (AiClientState.getGearKind() == com.example.content.GearKind.SWORD && !AiClientState.getMotion().isEmpty()) {
                AiClientState.addLog("[Request] 공격 모션: " + com.example.content.GearMotions.korean(AiClientState.getMotion()));
            }
            if (!idle.isEmpty()) {
                AiClientState.addLog("[Request] " + (AiClientState.getGearKind().worn() ? "착용 시: " : "대기 시: ") + idle);
            }
        }
        AiClientState.addLog("[Waiting...] " + what + " 설계 중입니다. 잠시 기다려 주세요.");
        AiClientState.sendPromptText(prompt);
        this.promptField.setText("");
        this.draftPrompt = "";
    }

    private void renderCreate(DrawContext ctx) {
        boolean object = AiClientState.isObjectMode();
        boolean gear = AiClientState.isGearMode();
        String header = gear ? "종류를 고르고 원하는 장비를 이야기해 주세요"
            : object ? "원하는 소품을 이야기해 주세요" : "원하는 건물을 이야기해 주세요";
        ctx.drawText(this.textRenderer, ellipsize(header, right() - left() - (gear ? 190 : 96)), left() + 2, top() + 5, INK_SOFT, false);

        int l = left(), r = right(), t = chatTop(), b = chatBottom();
        inset(ctx, l, t, r, b, PARCHMENT_DEEP);
        well(ctx, l, bottom() - 20, r - 68, bottom() - 2, this.promptField != null && this.promptField.isFocused());
        for (TextFieldWidget f : new TextFieldWidget[] { this.hitField, this.idleField }) {
            if (f != null && page == Page.CREATE) {
                well(ctx, f.getX() - 5, f.getY() - 5, f.getX() + f.getWidth() + 5, f.getY() + 17, f.isFocused());
            }
        }

        List<AiClientState.Entry> entries = new ArrayList<>(AiClientState.getEntries());
        if (entries.isEmpty()) {
            entries.add(new AiClientState.Entry(AiClientState.Speaker.SYSTEM,
                "아래에 " + (gear ? "장비를" : object ? "소품을" : "건물을") + " 적고 보내기를 누르세요.\n오른쪽 위 버튼으로 건물 / 소품 / 장비 모드를 바꿀 수 있어요."));
        }
        int maxBubble = (int) ((r - l - 12) * 0.82);
        List<List<OrderedText>> wrapped = new ArrayList<>();
        int total = 0;
        for (AiClientState.Entry e : entries) {
            int textW = e.speaker() == AiClientState.Speaker.SYSTEM ? r - l - 20 : maxBubble - 12;
            List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(e.text()), textW);
            wrapped.add(lines);
            total += entryHeight(e, lines.size()) + 6;
        }
        int view = b - t - 8;
        chatScroll = Math.max(0, Math.min(chatScroll, Math.max(0, total - view)));

        ctx.enableScissor(l + 1, t + 1, r - 1, b - 1);
        int y = b - 5 + chatScroll;
        for (int i = entries.size() - 1; i >= 0; i--) {
            AiClientState.Entry e = entries.get(i);
            int eh = entryHeight(e, wrapped.get(i).size());
            y -= eh;
            if (y + eh >= t && y <= b) {
                drawEntry(ctx, e, wrapped.get(i), l + 6, r - 6, y, maxBubble);
            }
            y -= 6;
        }
        ctx.disableScissor();
        if (chatScroll > 0) {
            String hint = "▼ 새 메시지";
            int hx = r - this.textRenderer.getWidth(hint) - 10;
            bevel(ctx, hx - 4, b - 16, r - 5, b - 3, WOOD, WOOD_LIGHT, WOOD_DARK, 1);
            ctx.drawText(this.textRenderer, hint, hx, b - 13, INK, false);
        }
    }

    private static int entryHeight(AiClientState.Entry e, int lines) {
        return e.speaker() == AiClientState.Speaker.SYSTEM ? lines * LINE + 4 : 12 + lines * LINE + 8;
    }

    /** My messages: green note on the right. AI: paper bubble on the left. Notices: centred text. */
    private void drawEntry(DrawContext ctx, AiClientState.Entry e, List<OrderedText> lines, int l, int r, int y, int maxBubble) {
        if (e.speaker() == AiClientState.Speaker.SYSTEM) {
            int ty = y + 2;
            for (OrderedText line : lines) {
                int lw = this.textRenderer.getWidth(line);
                ctx.drawText(this.textRenderer, line, (l + r - lw) / 2, ty, INK_SOFT, false);
                ty += LINE;
            }
            return;
        }
        boolean me = e.speaker() == AiClientState.Speaker.ME;
        int textW = 0;
        for (OrderedText line : lines) {
            textW = Math.max(textW, this.textRenderer.getWidth(line));
        }
        int bw = Math.min(maxBubble, Math.max(textW + 14, 40));
        int bl = me ? r - bw : l;
        int bt = y + 11;
        int bb = bt + lines.size() * LINE + 8;
        // Name above the bubble.
        String who = me ? "나" : "AI";
        int nameX = me ? r - this.textRenderer.getWidth(who) - 4 : l + 16;
        ctx.drawText(this.textRenderer, who, nameX, y + 1, me ? LEAF : TAG, false);
        if (!me) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(l + 2, y - 1, 0);
            ctx.getMatrices().scale(0.75f, 0.75f, 1f);
            ctx.drawItem(new ItemStack(Items.ENCHANTED_BOOK), 0, 0);
            ctx.getMatrices().pop();
        }
        int border = me ? LEAF : WOOD_DARK;
        ctx.fill(bl, bt, bl + bw, bb, border);
        ctx.fill(bl + 1, bt + 1, bl + bw - 1, bb - 1, me ? LEAF_PAPER : PAPER);
        // Little tail towards the speaker.
        int tx = me ? bl + bw - 10 : bl + 6;
        ctx.fill(tx, bt - 2, tx + 4, bt, border);
        ctx.fill(tx + 1, bt - 1, tx + 3, bt + 1, me ? LEAF_PAPER : PAPER);
        int ty = bt + 5;
        for (OrderedText line : lines) {
            ctx.drawText(this.textRenderer, line, bl + 7, ty, INK, false);
            ty += LINE;
        }
    }

    // ---------------------------------------------------------------- 보관함

    private List<BuildRecord> filtered() {
        List<BuildRecord> out = new ArrayList<>();
        for (BuildRecord r : BuildHistory.list()) {
            boolean object = "object".equals(r.kind);
            boolean gear = "gear".equals(r.kind);
            if ((filter == BUILDS && !object && !gear) || (filter == OBJECTS && object) || (filter == GEAR && gear)) {
                out.add(r);
            }
        }
        return out;
    }

    private BuildRecord selected() {
        if (selectedId == null) {
            return null;
        }
        for (BuildRecord r : BuildHistory.list()) {
            if (r.id.equals(selectedId)) {
                return r;
            }
        }
        selectedId = null;
        return null;
    }

    private int listTop() { return top() + 22; }

    private void buildHistory() {
        BuildRecord record = selected();
        if (record == null) {
            // Tabs: 건축 / 소품 / 장비; in 소품, "주변" lists the objects placed around me.
            int tx = right() - 3 * 44 - 8;
            String[] names = { "건축", "소품", "장비" };
            int[] filters = { BUILDS, OBJECTS, GEAR };
            for (int i = 0; i < 3; i++) {
                int target = filters[i];
                boolean on = filter == target || (target == OBJECTS && filter == NEARBY);
                button(names[i], tx + i * 48, top(), 44, b -> {
                    filter = target;
                    listScroll = 0;
                    rebuild();
                }).selected(on);
            }
            if (filter == OBJECTS || filter == NEARBY) {
                button("주변", tx - 48, top(), 44, b -> {
                    filter = filter == NEARBY ? OBJECTS : NEARBY;
                    listScroll = 0;
                    if (filter == NEARBY) {
                        AiClientNetworking.requestNearby();
                    }
                    rebuild();
                }).selected(filter == NEARBY);
            }
            if (filter == NEARBY) {
                // One "지우기" per visible row of placed objects.
                List<com.example.ai.build.ObjectService.Nearby> list = AiClientState.getNearby();
                int visible = Math.max(1, (bottom() - listTop() - 4) / ROW);
                for (int i = 0; i < visible && listScroll + i < list.size(); i++) {
                    com.example.ai.build.ObjectService.Nearby n = list.get(listScroll + i);
                    int y = listTop() + 3 + i * ROW + (ROW - 3 - 18) / 2;
                    this.addDrawableChild(new WoodButton(right() - 54, y, 48, 18, Text.literal("지우기"),
                        b -> AiClientNetworking.sendRemoveObject(0, n.tag), true));
                }
            }
            return;
        }
        int y = bottom() - 18;
        boolean object = "object".equals(record.kind);
        int slots = object ? 4 : 3;
        int quarter = (right() - left() - 4 * (slots - 1)) / slots;
        int scale = object ? SCALES[scaleIndex] : 100;
        String size = object ? " " + com.example.ai.build.RebuildService.scaleLabel(scale / 100.0) : "";
        int x = left();
        button("← 목록", x, y, quarter, b -> {
            selectedId = null;
            rebuild();
        });
        x += quarter + 4;
        if (object) {
            // Objects can be placed bigger or smaller: click to cycle the size.
            button("크기" + size, x, y, quarter, b -> {
                scaleIndex = (scaleIndex + 1) % SCALES.length;
                rebuild();
            });
            x += quarter + 4;
        }
        boolean gear = "gear".equals(record.kind);
        button("   손에 받기", x, y, quarter, b -> {
            AiClientNetworking.sendRebuild(withIcon(record), com.example.ai.build.RebuildService.MODE_GIVE, scale);
            AiClientState.addLog("[History] \"" + shortTitle(record.prompt) + "\"" + size
                + (gear ? " 장비를 요청했어요." : object ? " 소품 아이템을 요청했어요." : " 설계도를 요청했어요."));
        });
        this.icons.add(new IconAt(new ItemStack(gear ? com.example.content.GearKind.of(record.gear).icon
            : object ? Items.ARMOR_STAND : Items.PAPER), x + 3, y + 1));
        x += quarter + 4;
        this.addDrawableChild(new WoodButton(x, y, right() - x, 18,
            Text.literal(this.confirmDelete ? "정말 삭제?" : "삭제"), b -> {
                if (!this.confirmDelete) {
                    this.confirmDelete = true;
                    rebuild();
                    return;
                }
                BuildHistory.delete(record);
                selectedId = null;
                this.confirmDelete = false;
                rebuild();
            }, this.confirmDelete));
    }

    private void renderHistory(DrawContext ctx, int mouseX, int mouseY) {
        BuildRecord record = selected();
        if (record != null) {
            renderDetail(ctx, record);
            return;
        }
        if (filter == NEARBY) {
            renderNearby(ctx);
            return;
        }
        ctx.drawText(this.textRenderer, ellipsize("만든 것을 꺼내 쓸 수 있어요", right() - left() - 200), left() + 2, top() + 5, INK_SOFT, false);
        int l = left(), r = right(), t = listTop(), b = bottom();
        inset(ctx, l, t, r, b, PARCHMENT_DEEP);
        List<BuildRecord> records = filtered();
        if (records.isEmpty()) {
            String empty = "아직 비어 있어요. AI 공방에서 만들면 여기에 보관됩니다.";
            ctx.drawText(this.textRenderer, empty, (l + r - this.textRenderer.getWidth(empty)) / 2, t + 20, INK_SOFT, false);
            return;
        }
        int visible = Math.max(1, (b - t - 4) / ROW);
        listScroll = Math.max(0, Math.min(listScroll, records.size() - visible));
        int textX = l + ROW + 10;
        int textW = r - textX - 8;
        for (int i = 0; i < visible && listScroll + i < records.size(); i++) {
            BuildRecord rec = records.get(listScroll + i);
            int y = t + 3 + i * ROW;
            boolean hover = mouseX >= l + 3 && mouseX < r - 3 && mouseY >= y && mouseY < y + ROW - 3;
            ctx.fill(l + 3, y, r - 3, y + ROW - 3, hover ? 0xFFFFE9BC : PAPER);
            ctx.fill(l + 3, y + ROW - 4, r - 3, y + ROW - 3, PARCHMENT_LINE);
            if (hover) {
                outline(ctx, l + 3, y, r - 3, y + ROW - 3, TAG);
            }
            inset(ctx, l + 6, y + 3, l + ROW + 2, y + ROW - 6, 0xFFE8C48A); // item slot
            BuildPreview p = BuildPreview.of(rec);
            BuildPreview.draw(ctx, p.front, p.frontW, p.frontH, l + 8, y + 5, ROW - 10, ROW - 14);
            boolean object = "object".equals(rec.kind);
            boolean gear = "gear".equals(rec.kind);
            // No request text here (it is in the detail view): the picture, the kind and the size.
            String tag = gear ? "장비 · " + com.example.content.GearKind.of(rec.gear).korean : object ? "소품" : "건물";
            int tagW = this.textRenderer.getWidth(tag) + 6;
            ctx.fill(textX, y + 5, textX + tagW, y + 16, gear ? 0xFF4F7FA8 : object ? LEAF : TAG);
            ctx.drawText(this.textRenderer, tag, textX + 3, y + 7, PAPER, false);
            if (gear) {
                // Which kind of equipment, at a glance: the vanilla item in the picture's corner.
                ctx.drawItem(new ItemStack(com.example.content.GearKind.of(rec.gear).icon), l + ROW - 15, y + ROW - 23);
            }
            String sizeText = p.sizeX + " × " + p.sizeY + " × " + p.sizeZ + (object || gear ? " · 조각 " + p.blocks : "");
            ctx.drawText(this.textRenderer, ellipsize(sizeText, textW - tagW - 4), textX + tagW + 4, y + 7, INK, false);
            String info = DATE.format(new Date(rec.createdAt)) + " · " + rec.model;
            ctx.drawText(this.textRenderer, ellipsize(info, textW), textX, y + 21, INK_SOFT, false);
        }
        if (records.size() > visible) {
            String more = (listScroll + 1) + "-" + Math.min(records.size(), listScroll + visible) + " / " + records.size();
            ctx.drawText(this.textRenderer, more, r - this.textRenderer.getWidth(more) - 6, b - 11, INK_SOFT, false);
        }
    }

    /** Objects placed around me, nearest first, each with a 지우기 button (added in buildHistory). */
    private void renderNearby(DrawContext ctx) {
        ctx.drawText(this.textRenderer, ellipsize("주변 " + (int) com.example.ai.build.ObjectService.NEARBY_RADIUS
            + "블록 안의 소품 · 조준 후 Delete 두 번으로도 지워요", right() - left() - 170), left() + 2, top() + 5, INK_SOFT, false);
        int l = left(), r = right(), t = listTop(), b = bottom();
        inset(ctx, l, t, r, b, PARCHMENT_DEEP);
        List<com.example.ai.build.ObjectService.Nearby> list = AiClientState.getNearby();
        if (list.isEmpty()) {
            String empty = "근처에 놓인 소품이 없어요.";
            ctx.drawText(this.textRenderer, empty, (l + r - this.textRenderer.getWidth(empty)) / 2, t + 20, INK_SOFT, false);
            return;
        }
        int visible = Math.max(1, (b - t - 4) / ROW);
        listScroll = Math.max(0, Math.min(listScroll, list.size() - visible));
        for (int i = 0; i < visible && listScroll + i < list.size(); i++) {
            com.example.ai.build.ObjectService.Nearby n = list.get(listScroll + i);
            int y = t + 3 + i * ROW;
            ctx.fill(l + 3, y, r - 3, y + ROW - 3, PAPER);
            ctx.fill(l + 3, y + ROW - 4, r - 3, y + ROW - 3, PARCHMENT_LINE);
            inset(ctx, l + 6, y + 3, l + ROW + 2, y + ROW - 6, 0xFFE8C48A);
            BuildRecord source = recordForTag(n.tag);
            if (source != null) {
                BuildPreview p = BuildPreview.of(source);
                BuildPreview.draw(ctx, p.front, p.frontW, p.frontH, l + 8, y + 5, ROW - 10, ROW - 14);
            } else {
                ctx.drawItem(new ItemStack(Items.ARMOR_STAND), l + 12, y + 8); // not in my 보관함 (deleted, or another player's)
            }
            String name = n.name == null || n.name.isBlank()
                ? (source != null ? source.prompt.replace('\n', ' ') : "(이름 없는 소품)") : n.name;
            int textX = l + ROW + 10;
            ctx.drawText(this.textRenderer, ellipsize(name, r - textX - 64), textX, y + 7, INK, false);
            String info = String.format(java.util.Locale.ROOT, "%.0f블록 거리 · (%.0f, %.0f, %.0f) · 조각 %d개",
                n.distance, n.x, n.y, n.z, n.parts);
            ctx.drawText(this.textRenderer, ellipsize(info, r - textX - 64), textX, y + 21, INK_SOFT, false);
        }
    }

    /** A copy carrying the small front picture that becomes the item's icon. */
    private static BuildRecord withIcon(BuildRecord record) {
        if (!"object".equals(record.kind)) {
            return record;
        }
        BuildRecord copy = BuildRecord.fromJson(record.toJson());
        copy.icon = BuildPreview.of(record).icon();
        return copy;
    }

    /** Placed objects are tagged "aib_<record id>" or "aib_<record id>_<time>": find that record. */
    private static BuildRecord recordForTag(String tag) {
        if (tag == null || !tag.startsWith("aib_")) {
            return null;
        }
        String rest = tag.substring(4);
        int cut = rest.indexOf('_');
        String id = cut < 0 ? rest : rest.substring(0, cut);
        for (BuildRecord r : BuildHistory.list()) {
            if (r.id.equals(id)) {
                return r;
            }
        }
        return null;
    }

    private void renderDetail(DrawContext ctx, BuildRecord r) {
        int l = left(), rr = right(), t = top(), b = bottom() - 24;
        inset(ctx, l, t, rr, b, PARCHMENT_DEEP);
        BuildPreview p = BuildPreview.of(r);
        int width = rr - l - 14;
        ctx.enableScissor(l + 1, t + 1, rr - 1, b - 1);
        int x = l + 7;
        int y = t + 6 - detailScroll;
        boolean gear = "gear".equals(r.kind);
        boolean object = "object".equals(r.kind) || gear;
        ctx.drawText(this.textRenderer, gear ? "장비 주문서 · " + com.example.content.GearKind.of(r.gear).korean
            : object ? "소품 주문서" : "건물 주문서", x, y, TAG, false);
        y += LINE + 3;
        for (OrderedText line : this.textRenderer.wrapLines(Text.literal(r.prompt), width)) {
            ctx.drawText(this.textRenderer, line, x, y, INK, false);
            y += LINE;
        }
        y += 6;
        int room = b - (y + detailScroll) - (LINE + 3) - 8 - 3 * LINE - 4;
        int img = Math.max(48, Math.min(Math.min(140, (width - 8) / 2), room));
        ctx.drawText(this.textRenderer, ellipsize("정면", img), x, y, INK_SOFT, false);
        ctx.drawText(this.textRenderer, ellipsize("위에서", img), x + img + 8, y, INK_SOFT, false);
        y += LINE + 3;
        inset(ctx, x, y, x + img, y + img, 0xFFE8C48A);
        inset(ctx, x + img + 8, y, x + 2 * img + 8, y + img, 0xFFE8C48A);
        BuildPreview.draw(ctx, p.front, p.frontW, p.frontH, x + 3, y + 3, img - 6, img - 6);
        BuildPreview.draw(ctx, p.top, p.topW, p.topH, x + img + 11, y + 3, img - 6, img - 6);
        y += img + 8;
        String[] info = {
            "크기 " + p.sizeX + " × " + p.sizeY + " × " + p.sizeZ + " (가로 × 높이 × 세로) · "
                + (object ? "조각 " + p.blocks + "개" : "블록 " + p.blocks + "개"),
            "만든 날 " + DATE.format(new Date(r.createdAt)),
            "AI " + r.provider + " / " + r.model
        };
        for (String s : info) {
            for (OrderedText line : this.textRenderer.wrapLines(Text.literal(s), width)) {
                ctx.drawText(this.textRenderer, line, x, y, INK_SOFT, false);
                y += LINE;
            }
        }
        ctx.disableScissor();
        int contentHeight = y + detailScroll - t;
        detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, contentHeight - (b - t))));
    }

    // ---------------------------------------------------------------- 작업 일지

    private void buildLogs() {
        this.days = LogStore.days();
        dayIndex = Math.max(0, Math.min(dayIndex, Math.max(0, this.days.size() - 1)));
        this.fileLines = this.days.isEmpty() ? List.of() : LogStore.read(this.days.get(dayIndex));
        button("◀", left(), top(), 20, b -> {
            dayIndex++;
            fileScroll = 0;
            rebuild();
        }).active = dayIndex < this.days.size() - 1;
        button("▶", left() + 112, top(), 20, b -> {
            dayIndex--;
            fileScroll = 0;
            rebuild();
        }).active = dayIndex > 0;
        button("새로고침", right() - 70, top(), 70, b -> rebuild());
    }

    private void renderLogs(DrawContext ctx) {
        int l = left(), r = right();
        String day = this.days.isEmpty() ? "(기록 없음)" : this.days.get(dayIndex);
        ctx.drawText(this.textRenderer, day, l + 66 - this.textRenderer.getWidth(day) / 2, top() + 5, INK, false);
        int t = top() + 22;
        int b = bottom();
        inset(ctx, l, t, r, b, PAPER);
        // Ruled notebook lines.
        for (int ly = t + 13; ly < b - 2; ly += LINE) {
            ctx.fill(l + 3, ly, r - 3, ly + 1, 0x30A0602A);
        }
        if (this.fileLines.isEmpty()) {
            ctx.drawText(this.textRenderer, "아직 적힌 일지가 없어요.", l + 8, t + 6, INK_SOFT, false);
            return;
        }
        List<OrderedText> lines = new ArrayList<>();
        for (String line : this.fileLines) {
            lines.addAll(this.textRenderer.wrapLines(Text.literal(line.replace("\t", "  ")), r - l - 16));
        }
        int visible = Math.max(1, (b - t - 8) / LINE);
        fileScroll = Math.max(0, Math.min(fileScroll, Math.max(0, lines.size() - visible)));
        int end = lines.size() - fileScroll;
        int y = t + 4;
        for (int i = Math.max(0, end - visible); i < end; i++) {
            ctx.drawText(this.textRenderer, lines.get(i), l + 8, y, INK, false);
            y += LINE;
        }
    }

    // ---------------------------------------------------------------- frame and tabs

    private int tabX(int i) { return this.x0 + 18 + i * 32; }

    private int tabAt(double mx, double my) {
        for (int i = 0; i < TABS.length; i++) {
            if (mx >= tabX(i) && mx < tabX(i) + 28 && my >= this.y0 - 22 && my < this.y0 + 4) {
                return i;
            }
        }
        return -1;
    }

    private void renderTabs(DrawContext ctx, int mouseX, int mouseY, boolean activeOnly) {
        for (int i = 0; i < TABS.length; i++) {
            boolean active = TABS[i].page() == page;
            if (active != activeOnly) {
                continue;
            }
            int l = tabX(i), t = this.y0 - (active ? 24 : 20), r = l + 28, b = this.y0 + (active ? 9 : 2);
            boolean hover = tabAt(mouseX, mouseY) == i;
            bevel(ctx, l, t, r, b, active ? PARCHMENT : hover ? 0xFFF4B868 : WOOD, active ? 0xFFFFF0CF : WOOD_LIGHT,
                active ? PARCHMENT_LINE : WOOD_DARK, 2);
            if (active) {
                ctx.fill(l + 1, this.y0 + 4, r - 1, b, PARCHMENT); // open into the page
            }
            ctx.drawItem(new ItemStack(TABS[i].icon()), l + 6, t + 4);
        }
    }

    private void renderFrame(DrawContext ctx, int mouseX, int mouseY) {
        ctx.fill(0, 0, this.width, this.height, 0x55000000);
        renderTabs(ctx, mouseX, mouseY, false);
        frame(ctx, this.x0, this.y0, this.x0 + this.w, this.y0 + this.h);
        renderTabs(ctx, mouseX, mouseY, true);
        String title = switch (page) {
            case CREATE -> "AI 건축 공방";
            case HISTORY -> selected() == null ? "보관함" : "보관함 · 상세";
            case LOGS -> "작업 일지";
        };
        plaque(ctx, this.textRenderer, title, this.x0 + this.w / 2 + 30, this.y0 - 9, INK);
    }

    private static String shortTitle(String prompt) {
        String p = prompt == null ? "" : prompt.replace('\n', ' ');
        return p.length() > 30 ? p.substring(0, 30) + "…" : p;
    }

    private String ellipsize(String text, int width) {
        if (this.textRenderer.getWidth(text) <= width) {
            return text;
        }
        return this.textRenderer.trimToWidth(text, Math.max(0, width - this.textRenderer.getWidth("…"))) + "…";
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        layout();
        renderFrame(ctx, mouseX, mouseY);
        switch (page) {
            case CREATE -> renderCreate(ctx);
            case HISTORY -> renderHistory(ctx, mouseX, mouseY);
            case LOGS -> renderLogs(ctx);
        }
        super.render(ctx, mouseX, mouseY, delta);
        for (IconAt icon : this.icons) {
            ctx.drawItem(icon.stack(), icon.x(), icon.y());
        }
        for (TextFieldWidget f : new TextFieldWidget[] { this.hitField, this.idleField }) {
            if (f != null && page == Page.CREATE) {
                f.render(ctx, mouseX, mouseY, delta);
            }
        }
        if (this.promptField != null) {
            this.promptField.render(ctx, mouseX, mouseY, delta);
        }
        int hovered = tabAt(mouseX, mouseY);
        if (hovered >= 0) {
            String name = TABS[hovered].name();
            int tw = this.textRenderer.getWidth(name) + 8;
            int tx = mouseX + 8, ty = mouseY - 14;
            bevel(ctx, tx, ty, tx + tw, ty + 14, PAPER, 0xFFFFFBEF, PARCHMENT_LINE, 1);
            ctx.drawText(this.textRenderer, name, tx + 4, ty + 3, INK, false);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tab = tabAt(mouseX, mouseY);
        if (button == 0 && tab >= 0) {
            if (TABS[tab].page() == null) {
                this.client.setScreen(new AiSettingsScreen(this));
            } else if (TABS[tab].page() != page) {
                switchPage(TABS[tab].page());
            }
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int t = listTop() + 3;
        if (page == Page.HISTORY && selected() == null && filter != NEARBY && button == 0
            && mouseX >= left() && mouseX < right() && mouseY >= t && mouseY < bottom()) {
            int index = listScroll + (int) ((mouseY - t) / ROW);
            List<BuildRecord> records = filtered();
            if (index >= 0 && index < records.size()) {
                selectedId = records.get(index).id;
                detailScroll = 0;
                this.confirmDelete = false;
                rebuild();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0.0D || mouseX < left() || mouseX > right()) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int step = verticalAmount > 0 ? 1 : -1;
        switch (page) {
            case CREATE -> chatScroll = Math.max(0, chatScroll + step * 24);
            case LOGS -> fileScroll = Math.max(0, fileScroll + step * 3);
            case HISTORY -> {
                if (selected() != null) {
                    detailScroll = Math.max(0, detailScroll - step * 20);
                } else {
                    listScroll = Math.max(0, listScroll - step);
                    if (filter == NEARBY) {
                        rebuild(); // the 지우기 buttons follow the rows
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (page == Page.CREATE && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            sendBuild();
            return true;
        }
        if (this.promptField != null && this.promptField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext drawContext, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
