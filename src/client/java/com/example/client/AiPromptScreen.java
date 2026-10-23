package com.example.client;

import com.example.ai.build.BuildRecord;
import com.example.client.network.AiClientNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Main window: a wooden frame with a menu on the left and the page on the right.
 *   AI로 만들기   - chat with the AI: request, review, place (설계 배치하기), cancel, undo
 *   내 오브젝트   - saved builds and objects with previews; open one to place it again
 *   더보기        - 로그 (saved logs by day), 설정 (settings)
 */
public class AiPromptScreen extends Screen {

    private enum Page { CREATE, HISTORY, LOGS }

    // Palette: dark wood frame, warm parchment text, dark chat area.
    private static final int FRAME_OUT = 0xFF24170D;
    private static final int FRAME = 0xFF6E4C2E;
    private static final int SIDEBAR = 0xFF56391F;
    private static final int HEADER = 0xFF4A3322;
    private static final int LINE_LIGHT = 0xFF8C6B47;
    private static final int CHAT_BG = 0xFF24201C;
    private static final int CARD = 0xFF36302A;
    private static final int CARD_BORDER = 0xFF5E5040;
    private static final int CARD_ME = 0xFFC9A86A;
    private static final int TITLE = 0xFFF3E6C8;
    private static final int SUBTITLE = 0xFFD2BE98;
    private static final int TEXT = 0xFFEDE3CF;
    private static final int DIM = 0xFF9C8F7C;
    private static final int SELECTED = 0xFFE8C26A;
    private static final int SIDEBAR_WIDTH = 118;
    private static final int LINE = 10;
    private static final int ROW = 40;
    private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    // Kept across re-inits (the screen rebuilds its widgets when state changes).
    private static Page page = Page.CREATE;
    private static boolean moreOpen = false;
    private static String selectedId = null;
    private static int filter = 0;        // 0 all, 1 builds, 2 objects
    private static int chatScroll = 0;    // pixels up from the bottom
    private static int listScroll = 0;    // rows
    private static int detailScroll = 0;  // pixels
    private static int fileScroll = 0;    // lines up from the bottom
    private static int dayIndex = 0;

    private final List<IconAt> icons = new ArrayList<>();
    private record IconAt(ItemStack stack, int x, int y) {
    }

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

    public void onStateUpdated(boolean rebuildWidgets) {
        if (rebuildWidgets) {
            rebuild();
        }
    }

    private void rebuild() {
        if (this.promptField != null) {
            this.draftPrompt = this.promptField.getText();
        }
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
        buildSidebar();
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
        this.w = Math.min(this.width - 16, 500);
        this.h = Math.min(this.height - 16, 320);
        this.x0 = (this.width - this.w) / 2;
        this.y0 = (this.height - this.h) / 2;
    }

    // Right-hand page geometry.
    private int rx() { return this.x0 + 4 + SIDEBAR_WIDTH + 4; }
    private int rr() { return this.x0 + this.w - 4; }
    private int contentTop() { return this.y0 + 46; }
    private int chatBottom() { return this.y0 + this.h - 58; }

    private ButtonWidget button(String label, int x, int y, int width, ButtonWidget.PressAction action) {
        return this.addDrawableChild(ButtonWidget.builder(Text.literal(label), action).dimensions(x, y, width, 20).build());
    }

    // ---------------------------------------------------------------- sidebar

    private void buildSidebar() {
        int x = this.x0 + 10;
        int bw = SIDEBAR_WIDTH - 12;
        int y = this.y0 + 44;
        menu("   AI로 만들기", Items.WRITABLE_BOOK, x, y, bw, Page.CREATE);
        y += 24;
        menu("   내 오브젝트", Items.CHEST, x, y, bw, Page.HISTORY);
        y += 24;
        button("   더보기 " + (moreOpen ? "▴" : "▾"), x, y, bw, b -> {
            moreOpen = !moreOpen;
            rebuild();
        });
        this.icons.add(new IconAt(new ItemStack(Items.BUNDLE), x + 3, y + 2));
        if (moreOpen) {
            y += 22;
            ButtonWidget logs = button("로그", x + 10, y, bw - 10, b -> switchPage(Page.LOGS));
            logs.active = page != Page.LOGS;
            y += 22;
            button("설정", x + 10, y, bw - 10, b -> this.client.setScreen(new AiSettingsScreen(this)));
        }
        button("닫기", x, this.y0 + this.h - 28, bw, b -> this.close());
    }

    private void menu(String label, net.minecraft.item.Item icon, int x, int y, int width, Page target) {
        ButtonWidget b = button(label, x, y, width, btn -> switchPage(target));
        b.active = page != target;
        this.icons.add(new IconAt(new ItemStack(icon), x + 3, y + 2));
    }

    private void switchPage(Page target) {
        page = target;
        this.confirmDelete = false;
        if (target == Page.LOGS) {
            fileScroll = 0;
        }
        rebuild();
    }

    // ---------------------------------------------------------------- AI로 만들기

    private void buildCreate() {
        boolean object = AiClientState.isObjectMode();
        button(object ? "모드: 소품" : "모드: 건물", rr() - 92, this.y0 + 10, 86, b -> AiClientState.toggleObjectMode());

        boolean pending = AiClientState.hasPendingApproval();
        int y = this.y0 + this.h - 52;
        ButtonWidget place = button("설계 배치하기", rx(), y, 86, b -> AiClientState.approve());
        place.active = pending;
        ButtonWidget cancel = button("취소", rx() + 90, y, 50, b -> AiClientState.cancel());
        cancel.active = pending;
        button("   되돌리기", rx() + 144, y, 76, b -> {
            AiClientState.undo();
            chatScroll = 0;
        });
        this.icons.add(new IconAt(new ItemStack(Items.CLOCK), rx() + 147, y + 2));
        if (AiClientState.isWebSearchAvailable()) {
            button(AiClientState.willUseWebSearch() ? "웹 검색: 켬" : "웹 검색: 끔", rr() - 76, y, 76, b -> {
                AiClientState.toggleSessionWebSearch();
                rebuild();
            });
        }

        int fieldY = this.y0 + this.h - 27;
        this.promptField = new TextFieldWidget(this.textRenderer, rx(), fieldY, rr() - rx() - 66, 20, Text.literal(""));
        this.promptField.setMaxLength(220); // the chat command that carries it is limited to 256 characters
        this.promptField.setPlaceholder(Text.literal(object ? "만들 소품을 설명해 주세요" : "지을 건물을 설명해 주세요"));
        this.promptField.setText(this.draftPrompt);
        this.promptField.setFocused(true);
        this.addSelectableChild(this.promptField);
        this.setFocused(this.promptField);
        button("   보내기", rr() - 62, fieldY, 62, b -> sendBuild());
        this.icons.add(new IconAt(new ItemStack(Items.FEATHER), rr() - 59, fieldY + 2));
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
        AiClientState.addLog("[Waiting...] " + (AiClientState.isObjectMode() ? "소품" : "건물") + " 설계 중입니다. 잠시 기다려 주세요.");
        AiClientState.sendPromptText(prompt);
        this.promptField.setText("");
        this.draftPrompt = "";
    }

    private void renderCreate(DrawContext ctx) {
        int left = rx(), right = rr(), top = contentTop(), bottom = chatBottom();
        ctx.fill(left, top, right, bottom, CHAT_BG);

        List<AiClientState.Entry> entries = new ArrayList<>(AiClientState.getEntries());
        if (entries.isEmpty()) {
            entries.add(new AiClientState.Entry(AiClientState.Speaker.SYSTEM,
                "원하는 " + (AiClientState.isObjectMode() ? "소품" : "건물") + "을 아래에 적고 보내기를 누르세요.\n"
                    + "오른쪽 위 버튼으로 건물 / 소품 모드를 바꿀 수 있어요."));
        }
        int cardW = right - left - 12;
        int textW = cardW - 10;
        List<List<OrderedText>> wrapped = new ArrayList<>();
        int total = 0;
        for (AiClientState.Entry e : entries) {
            List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(e.text()), textW);
            wrapped.add(lines);
            total += cardHeight(lines.size()) + 4;
        }
        int view = bottom - top - 8;
        chatScroll = Math.max(0, Math.min(chatScroll, Math.max(0, total - view)));

        ctx.enableScissor(left, top, right, bottom);
        int y = bottom - 4 + chatScroll;
        for (int i = entries.size() - 1; i >= 0; i--) {
            int ch = cardHeight(wrapped.get(i).size());
            y -= ch;
            if (y + ch >= top && y <= bottom) {
                drawCard(ctx, entries.get(i), wrapped.get(i), left + 6, y, cardW, ch);
            }
            y -= 4;
        }
        ctx.disableScissor();
        if (chatScroll > 0) {
            String hint = "▼ 최신 메시지";
            ctx.drawText(this.textRenderer, hint, right - this.textRenderer.getWidth(hint) - 6, bottom - 11, SELECTED, true);
        }
    }

    private static int cardHeight(int lines) {
        return 19 + lines * LINE + 4;
    }

    private void drawCard(DrawContext ctx, AiClientState.Entry e, List<OrderedText> lines, int x, int y, int cw, int ch) {
        boolean me = e.speaker() == AiClientState.Speaker.ME;
        outline(ctx, x, y, x + cw, y + ch, me ? CARD_ME : CARD_BORDER);
        ctx.fill(x + 1, y + 1, x + cw - 1, y + ch - 1, CARD);
        ItemStack icon = new ItemStack(switch (e.speaker()) {
            case ME -> Items.FEATHER;
            case AI -> Items.ENCHANTED_BOOK;
            case SYSTEM -> Items.COMPASS;
        });
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 4, y + 3, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1f);
        ctx.drawItem(icon, 0, 0);
        ctx.getMatrices().pop();
        String who = switch (e.speaker()) {
            case ME -> "나";
            case AI -> "AI";
            case SYSTEM -> "안내";
        };
        ctx.drawText(this.textRenderer, who, x + 19, y + 5, me ? CARD_ME : SUBTITLE, false);
        int ty = y + 18;
        for (OrderedText line : lines) {
            ctx.drawText(this.textRenderer, line, x + 6, ty, TEXT, false);
            ty += LINE;
        }
    }

    // ---------------------------------------------------------------- 내 오브젝트

    private List<BuildRecord> filtered() {
        List<BuildRecord> out = new ArrayList<>();
        for (BuildRecord r : BuildHistory.list()) {
            boolean object = "object".equals(r.kind);
            if (filter == 0 || (filter == 1 && !object) || (filter == 2 && object)) {
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

    private void buildHistory() {
        BuildRecord record = selected();
        if (record == null) {
            String[] names = { "보기: 전체", "보기: 건물", "보기: 소품" };
            button(names[filter], rr() - 92, this.y0 + 10, 86, b -> {
                filter = (filter + 1) % 3;
                listScroll = 0;
                rebuild();
            });
            return;
        }
        int y = this.y0 + this.h - 27;
        int third = (rr() - rx() - 8) / 3;
        button("← 목록", rx(), y, third, b -> {
            selectedId = null;
            rebuild();
        });
        button("여기에 배치", rx() + third + 4, y, third, b -> {
            AiClientNetworking.sendRebuild(record);
            AiClientState.addLog("[History] \"" + shortTitle(record.prompt) + "\" 배치를 요청했어요. 내 앞에, 바라보는 방향으로 놓입니다.");
        });
        button(this.confirmDelete ? "정말 삭제?" : "삭제", rx() + 2 * third + 8, y, third, b -> {
            if (!this.confirmDelete) {
                this.confirmDelete = true;
                rebuild();
                return;
            }
            BuildHistory.delete(record);
            selectedId = null;
            this.confirmDelete = false;
            rebuild();
        });
    }

    private void renderHistory(DrawContext ctx, int mouseX, int mouseY) {
        int left = rx(), right = rr(), top = contentTop(), bottom = this.y0 + this.h - 6;
        BuildRecord record = selected();
        if (record != null) {
            renderDetail(ctx, record, left, right, top, this.y0 + this.h - 32);
            return;
        }
        ctx.fill(left, top, right, bottom, CHAT_BG);
        List<BuildRecord> records = filtered();
        if (records.isEmpty()) {
            ctx.drawText(this.textRenderer, "아직 없어요. 설계를 배치하면 여기에 저장됩니다.", left + 6, top + 8, DIM, false);
            return;
        }
        int visible = Math.max(1, (bottom - top - 4) / ROW);
        listScroll = Math.max(0, Math.min(listScroll, records.size() - visible));
        int textX = left + ROW + 8;
        int textW = right - textX - 6;
        for (int i = 0; i < visible && listScroll + i < records.size(); i++) {
            BuildRecord r = records.get(listScroll + i);
            int y = top + 3 + i * ROW;
            boolean hover = mouseX >= left + 3 && mouseX < right - 3 && mouseY >= y && mouseY < y + ROW - 3;
            outline(ctx, left + 3, y, right - 3, y + ROW - 3, hover ? CARD_ME : CARD_BORDER);
            ctx.fill(left + 4, y + 1, right - 4, y + ROW - 4, CARD);
            BuildPreview p = BuildPreview.of(r);
            ctx.fill(left + 7, y + 3, left + ROW + 1, y + ROW - 6, 0xFF15130F);
            BuildPreview.draw(ctx, p.top, p.topW, p.topH, left + 8, y + 4, ROW - 8, ROW - 11);
            String tag = "object".equals(r.kind) ? "[소품] " : "[건물] ";
            ctx.drawText(this.textRenderer, ellipsize(tag + r.prompt.replace('\n', ' '), textW), textX, y + 6, TEXT, false);
            String info = DATE.format(new Date(r.createdAt)) + "  " + p.sizeX + "×" + p.sizeY + "×" + p.sizeZ + "  " + r.model;
            ctx.drawText(this.textRenderer, ellipsize(info, textW), textX, y + 20, DIM, false);
        }
        if (records.size() > visible) {
            String more = (listScroll + 1) + "-" + Math.min(records.size(), listScroll + visible) + " / " + records.size();
            ctx.drawText(this.textRenderer, more, right - this.textRenderer.getWidth(more) - 6, bottom - 11, DIM, false);
        }
    }

    private void renderDetail(DrawContext ctx, BuildRecord r, int left, int right, int top, int bottom) {
        ctx.fill(left, top, right, bottom, CHAT_BG);
        BuildPreview p = BuildPreview.of(r);
        int width = right - left - 12;
        ctx.enableScissor(left, top, right, bottom);
        int x = left + 6;
        int y = top + 6 - detailScroll;
        ctx.drawText(this.textRenderer, "object".equals(r.kind) ? "소품 요청" : "건물 요청", x, y, SELECTED, false);
        y += LINE + 3;
        for (OrderedText line : this.textRenderer.wrapLines(Text.literal(r.prompt), width)) {
            ctx.drawText(this.textRenderer, line, x, y, TEXT, false);
            y += LINE;
        }
        y += 6;
        // Fit both pictures and the four info lines between the request text and the buttons.
        int room = bottom - (y + detailScroll) - (LINE + 3) - 8 - 5 * LINE - 6;
        int img = Math.max(48, Math.min(Math.min(140, (width - 8) / 2), room));
        ctx.drawText(this.textRenderer, "위에서 (앞쪽이 위)", x, y, SUBTITLE, false);
        ctx.drawText(this.textRenderer, "정면에서", x + img + 8, y, SUBTITLE, false);
        y += LINE + 3;
        outline(ctx, x, y, x + img, y + img, CARD_BORDER);
        ctx.fill(x + 1, y + 1, x + img - 1, y + img - 1, 0xFF15130F);
        outline(ctx, x + img + 8, y, x + 2 * img + 8, y + img, CARD_BORDER);
        ctx.fill(x + img + 9, y + 1, x + 2 * img + 7, y + img - 1, 0xFF15130F);
        BuildPreview.draw(ctx, p.top, p.topW, p.topH, x + 3, y + 3, img - 6, img - 6);
        BuildPreview.draw(ctx, p.front, p.frontW, p.frontH, x + img + 11, y + 3, img - 6, img - 6);
        y += img + 8;
        String[] info = {
            "크기: " + p.sizeX + " × " + p.sizeY + " × " + p.sizeZ + " (가로 × 높이 × 세로), "
                + ("object".equals(r.kind) ? "조각 " + p.blocks + "개" : "블록 " + p.blocks + "개"),
            "만든 날: " + DATE.format(new Date(r.createdAt)),
            "AI: " + r.provider + " / " + r.model,
            "여기에 배치: 내 앞에, 지금 바라보는 방향으로 놓입니다. 되돌리기로 취소할 수 있어요."
        };
        for (String s : info) {
            for (OrderedText line : this.textRenderer.wrapLines(Text.literal(s), width)) {
                ctx.drawText(this.textRenderer, line, x, y, s.startsWith("여기에") ? DIM : TEXT, false);
                y += LINE;
            }
        }
        ctx.disableScissor();
        int contentHeight = y + detailScroll - top;
        detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, contentHeight - (bottom - top))));
    }

    // ---------------------------------------------------------------- 로그

    private void buildLogs() {
        this.days = LogStore.days();
        dayIndex = Math.max(0, Math.min(dayIndex, Math.max(0, this.days.size() - 1)));
        this.fileLines = this.days.isEmpty() ? List.of() : LogStore.read(this.days.get(dayIndex));
        ButtonWidget older = button("<", rx(), contentTop(), 20, b -> {
            dayIndex++;
            fileScroll = 0;
            rebuild();
        });
        older.active = dayIndex < this.days.size() - 1;
        ButtonWidget newer = button(">", rx() + 110, contentTop(), 20, b -> {
            dayIndex--;
            fileScroll = 0;
            rebuild();
        });
        newer.active = dayIndex > 0;
        button("새로고침", rr() - 92, this.y0 + 10, 86, b -> rebuild());
    }

    private void renderLogs(DrawContext ctx) {
        int left = rx(), right = rr();
        String day = this.days.isEmpty() ? "(기록 없음)" : this.days.get(dayIndex);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(day), left + 65, contentTop() + 6, TITLE);
        int top = contentTop() + 24;
        int bottom = this.y0 + this.h - 6;
        ctx.fill(left, top, right, bottom, CHAT_BG);
        if (this.fileLines.isEmpty()) {
            ctx.drawText(this.textRenderer, "아직 기록이 없어요.", left + 6, top + 6, DIM, false);
            return;
        }
        List<OrderedText> lines = new ArrayList<>();
        for (String line : this.fileLines) {
            lines.addAll(this.textRenderer.wrapLines(Text.literal(line.replace("\t", "  ")), right - left - 12));
        }
        int visible = Math.max(1, (bottom - top - 8) / LINE);
        fileScroll = Math.max(0, Math.min(fileScroll, Math.max(0, lines.size() - visible)));
        int end = lines.size() - fileScroll;
        int y = top + 4;
        for (int i = Math.max(0, end - visible); i < end; i++) {
            ctx.drawText(this.textRenderer, lines.get(i), left + 6, y, TEXT, false);
            y += LINE;
        }
    }

    // ---------------------------------------------------------------- frame

    private void renderFrame(DrawContext ctx) {
        ctx.fill(0, 0, this.width, this.height, 0x66000000);
        int x1 = this.x0 + this.w, y1 = this.y0 + this.h;
        ctx.fill(this.x0 - 2, this.y0 - 2, x1 + 2, y1 + 2, FRAME_OUT);
        ctx.fill(this.x0, this.y0, x1, y1, FRAME);
        // Corner studs.
        for (int[] c : new int[][] { { this.x0 - 2, this.y0 - 2 }, { x1 - 2, this.y0 - 2 }, { this.x0 - 2, y1 - 2 }, { x1 - 2, y1 - 2 } }) {
            ctx.fill(c[0], c[1], c[0] + 4, c[1] + 4, LINE_LIGHT);
        }
        // Sidebar.
        int sx = this.x0 + 4;
        ctx.fill(sx, this.y0 + 4, sx + SIDEBAR_WIDTH, y1 - 4, SIDEBAR);
        outline(ctx, sx, this.y0 + 4, sx + SIDEBAR_WIDTH, y1 - 4, LINE_LIGHT);
        ctx.drawText(this.textRenderer, "AI BUILDER", sx + 7, this.y0 + 12, TITLE, true);
        ctx.drawText(this.textRenderer, "블록 건축 도우미", sx + 7, this.y0 + 25, SUBTITLE, false);
        for (int i = 0; i < 6; i++) { // faint plank lines
            int ly = this.y0 + 150 + i * 22;
            if (ly < y1 - 36) {
                ctx.fill(sx + 6, ly, sx + SIDEBAR_WIDTH - 6, ly + 1, 0x30FFFFFF);
            }
        }
        // Page header.
        int left = rx(), right = rr();
        ctx.fill(left, this.y0 + 4, right, this.y0 + 40, HEADER);
        outline(ctx, left, this.y0 + 4, right, this.y0 + 40, LINE_LIGHT);
        String title;
        String subtitle;
        ItemStack icon;
        switch (page) {
            case CREATE -> {
                title = "AI 작업실";
                subtitle = AiClientState.isObjectMode() ? "원하는 소품을 이야기해 주세요" : "원하는 건물을 이야기해 주세요";
                icon = new ItemStack(Items.WRITABLE_BOOK);
            }
            case HISTORY -> {
                title = selected() == null ? "내 오브젝트" : "오브젝트 정보";
                subtitle = "만든 건물과 소품을 다시 배치할 수 있어요";
                icon = new ItemStack(Items.CHEST);
            }
            default -> {
                title = "로그";
                subtitle = "날짜별 전체 기록";
                icon = new ItemStack(Items.PAPER);
            }
        }
        ctx.drawItem(icon, left + 6, this.y0 + 7);
        ctx.drawText(this.textRenderer, title, left + 26, this.y0 + 11, TITLE, true);
        ctx.drawText(this.textRenderer, ellipsize(subtitle, right - left - 112), left + 8, this.y0 + 27, SUBTITLE, false);
    }

    private static void outline(DrawContext ctx, int l, int t, int r, int b, int color) {
        ctx.fill(l, t, r, t + 1, color);
        ctx.fill(l, b - 1, r, b, color);
        ctx.fill(l, t, l + 1, b, color);
        ctx.fill(r - 1, t, r, b, color);
    }

    private String ellipsize(String text, int width) {
        if (this.textRenderer.getWidth(text) <= width) {
            return text;
        }
        return this.textRenderer.trimToWidth(text, Math.max(0, width - this.textRenderer.getWidth("…"))) + "…";
    }

    private static String shortTitle(String prompt) {
        String p = prompt == null ? "" : prompt.replace('\n', ' ');
        return p.length() > 30 ? p.substring(0, 30) + "…" : p;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        layout();
        renderFrame(ctx);
        switch (page) {
            case CREATE -> renderCreate(ctx);
            case HISTORY -> renderHistory(ctx, mouseX, mouseY);
            case LOGS -> renderLogs(ctx);
        }
        super.render(ctx, mouseX, mouseY, delta);
        for (IconAt icon : this.icons) {
            ctx.drawItem(icon.stack(), icon.x(), icon.y());
        }
        // Mark the open page in the menu.
        int sy = page == Page.CREATE ? this.y0 + 44 : page == Page.HISTORY ? this.y0 + 68 : -1;
        if (sy > 0) {
            outline(ctx, this.x0 + 9, sy - 1, this.x0 + 9 + SIDEBAR_WIDTH - 10, sy + 21, SELECTED);
        }
        if (this.promptField != null) {
            this.promptField.render(ctx, mouseX, mouseY, delta);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int top = contentTop() + 3;
        if (page == Page.HISTORY && selected() == null && button == 0
            && mouseX >= rx() && mouseX < rr() && mouseY >= top && mouseY < this.y0 + this.h - 6) {
            int index = listScroll + (int) ((mouseY - top) / ROW);
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
        if (verticalAmount == 0.0D || mouseX < rx() || mouseX > rr()) {
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
