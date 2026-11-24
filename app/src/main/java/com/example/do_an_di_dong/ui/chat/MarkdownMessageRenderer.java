package com.example.do_an_di_dong.ui.chat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.do_an_di_dong.R;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bộ dựng giao diện (Renderer) Markdown & Bảng biểu thuần Android (Native View) cho bong bóng chat AI.
 * Được gọi trực tiếp từ ChatAdapter để chuyển đổi văn bản Markdown (tiêu đề, in đậm/nghiêng, code block,
 * danh sách bullet/đánh số, trích dẫn, và bảng kẻ ô có cuộn ngang) thành các View Android trong item_chat_message.xml.
 */
public final class MarkdownMessageRenderer {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^\\s*(#{1,4})\\s+(.+?)\\s*$");
    private static final Pattern HR_PATTERN = Pattern.compile("^\\s*([-*_])(?:\\s*\\1){2,}\\s*$");
    private static final Pattern BULLET_PATTERN = Pattern.compile("^(\\s*)[-*•+]\\s+(.+)$");
    private static final Pattern ORDERED_PATTERN = Pattern.compile("^(\\s*)(\\d+[.)])\\s+(.+)$");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("^\\s*>\\s?(.*)$");
    private static final Pattern SEPARATOR_CELL_PATTERN = Pattern.compile("^:?-{2,}:?$");

    private static final Pattern INLINE_LINK_OR_CODE_PATTERN =
            Pattern.compile("(`[^`]+`|\\[[^\\]]+]\\((https?://[^)\\s]+)\\))");
    private static final Pattern BOLD_STAR_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern BOLD_UNDER_PATTERN = Pattern.compile("__(.+?)__");
    private static final Pattern STRIKE_PATTERN = Pattern.compile("~~(.+?)~~");
    private static final Pattern ITALIC_STAR_PATTERN = Pattern.compile("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)");
    private static final Pattern ITALIC_UNDER_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])_([^_\\n]+)_(?![A-Za-z0-9_])");

    private MarkdownMessageRenderer() {
    }

    /**
     * Hàm chính phân tích chuỗi Markdown thô theo từng dòng (block-level) và gắn các View con tương ứng vào container.
     * Có sử dụng View tag để cache chuỗi đã chuẩn hóa, tránh dựng lại View không cần thiết khi RecyclerView cuộn.
     */
    public static void render(LinearLayout container, String rawMarkdown) {
        if (container == null) {
            return;
        }
        String normalized = normalizeMarkdown(rawMarkdown);
        // Kiểm tra cache trên tag của View: nếu nội dung không đổi thì bỏ qua việc removeAllViews và render lại
        Object prevTag = container.getTag(R.id.layoutAiMarkdownBody);
        if (normalized.equals(prevTag) && container.getChildCount() > 0) {
            return;
        }
        container.setTag(R.id.layoutAiMarkdownBody, normalized);
        container.removeAllViews();

        Context ctx = container.getContext();
        String[] lines = normalized.split("\n", -1);
        int i = 0;
        boolean hasPreviousBlock = false;

        while (i < lines.length) {
            String line = lines[i];
            if (line.trim().isEmpty()) {
                i++;
                continue;
            }

            // 1. Fenced code block (``` ... ```)
            if (line.trim().startsWith("```")) {
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    if (code.length() > 0) code.append('\n');
                    code.append(lines[i]);
                    i++;
                }
                if (i < lines.length) i++;
                addViewWithSpacing(container, createCodeBlockView(ctx, code.toString()), hasPreviousBlock, 6);
                hasPreviousBlock = true;
                continue;
            }

            // 2. Markdown table (Header line with '|' + separator line ':---|---')
            if (i + 1 < lines.length && isPotentialTableStart(lines[i], lines[i + 1])) {
                List<String> tableLines = new ArrayList<>();
                tableLines.add(lines[i]);
                tableLines.add(lines[i + 1]);
                i += 2;
                while (i < lines.length && !lines[i].trim().isEmpty() && lines[i].contains("|")
                        && !lines[i].trim().startsWith("```")) {
                    tableLines.add(lines[i]);
                    i++;
                }
                View tableView = createTableView(ctx, tableLines);
                if (tableView != null) {
                    addViewWithSpacing(container, tableView, hasPreviousBlock, 8);
                    hasPreviousBlock = true;
                    continue;
                }
            }

            // 3. Heading (#, ##, ###, ####)
            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                int level = headingMatcher.group(1).length();
                String title = headingMatcher.group(2);
                addViewWithSpacing(container, createHeadingView(ctx, level, title), hasPreviousBlock, 8);
                hasPreviousBlock = true;
                i++;
                continue;
            }

            // 4. Horizontal rule (--- / ***)
            if (HR_PATTERN.matcher(line).matches()) {
                addViewWithSpacing(container, createDividerView(ctx), hasPreviousBlock, 8);
                hasPreviousBlock = true;
                i++;
                continue;
            }

            // 5. Blockquote (> ...)
            Matcher quoteMatcher = QUOTE_PATTERN.matcher(line);
            if (quoteMatcher.matches()) {
                StringBuilder quoteText = new StringBuilder();
                while (i < lines.length) {
                    Matcher qm = QUOTE_PATTERN.matcher(lines[i]);
                    if (!qm.matches()) break;
                    if (quoteText.length() > 0) quoteText.append('\n');
                    quoteText.append(qm.group(1));
                    i++;
                }
                addViewWithSpacing(container, createQuoteView(ctx, quoteText.toString()), hasPreviousBlock, 6);
                hasPreviousBlock = true;
                continue;
            }

            // 6. Bullet or ordered list block
            if (BULLET_PATTERN.matcher(line).matches() || ORDERED_PATTERN.matcher(line).matches()) {
                LinearLayout listLayout = new LinearLayout(ctx);
                listLayout.setOrientation(LinearLayout.VERTICAL);
                while (i < lines.length) {
                    String current = lines[i];
                    Matcher bm = BULLET_PATTERN.matcher(current);
                    Matcher om = ORDERED_PATTERN.matcher(current);
                    if (bm.matches()) {
                        int indentLevel = Math.min(3, bm.group(1).length() / 2);
                        listLayout.addView(createListItemView(ctx, "•", bm.group(2), indentLevel));
                        i++;
                    } else if (om.matches()) {
                        int indentLevel = Math.min(3, om.group(1).length() / 2);
                        listLayout.addView(createListItemView(ctx, om.group(2), om.group(3), indentLevel));
                        i++;
                    } else {
                        break;
                    }
                }
                addViewWithSpacing(container, listLayout, hasPreviousBlock, 6);
                hasPreviousBlock = true;
                continue;
            }

            // 7. Standard paragraph lines
            StringBuilder paragraph = new StringBuilder();
            while (i < lines.length) {
                String current = lines[i];
                if (current.trim().isEmpty()
                        || current.trim().startsWith("```")
                        || HEADING_PATTERN.matcher(current).matches()
                        || HR_PATTERN.matcher(current).matches()
                        || QUOTE_PATTERN.matcher(current).matches()
                        || BULLET_PATTERN.matcher(current).matches()
                        || ORDERED_PATTERN.matcher(current).matches()
                        || (i + 1 < lines.length && isPotentialTableStart(current, lines[i + 1]))) {
                    break;
                }
                if (paragraph.length() > 0) {
                    paragraph.append('\n');
                }
                paragraph.append(current.trim());
                i++;
            }
            if (paragraph.length() > 0) {
                addViewWithSpacing(container, createParagraphView(ctx, paragraph.toString()), hasPreviousBlock, 6);
                hasPreviousBlock = true;
            } else {
                i++;
            }
        }
    }

    /**
     * Chuẩn hóa dấu xuống dòng (\r\n hoặc \r về \n) và cắt khoảng trắng thừa ở 2 đầu chuỗi Markdown.
     */
    private static String normalizeMarkdown(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    /**
     * Thêm View khối (block) vào LinearLayout cha kèm khoảng cách lề trên (topMargin) nếu không phải khối đầu tiên.
     */
    private static void addViewWithSpacing(LinearLayout parent, View child, boolean addTopMargin, int topDp) {
        LinearLayout.LayoutParams lp = child.getLayoutParams() instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) child.getLayoutParams()
                : new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (addTopMargin) {
            lp.topMargin = dp(parent.getContext(), topDp);
        }
        child.setLayoutParams(lp);
        parent.addView(child);
    }

    /**
     * Kiểm tra xem 2 dòng liên tiếp có phải là phần mở đầu của một bảng Markdown hợp lệ hay không
     * (dòng tiêu đề chứa dấu '|' và dòng phân cách khớp định dạng ':---', '---:', ':---:').
     */
    private static boolean isPotentialTableStart(String headerLine, String separatorLine) {
        if (!headerLine.contains("|") || !separatorLine.contains("|")) {
            return false;
        }
        List<String> headers = splitTableCells(headerLine);
        List<String> separators = splitTableCells(separatorLine);
        if (headers.size() < 2 || separators.size() < 2) {
            return false;
        }
        for (String cell : separators) {
            String trimmed = cell.trim();
            if (!SEPARATOR_CELL_PATTERN.matcher(trimmed).matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tách một dòng trong bảng Markdown thành danh sách các ô (cell) theo ký tự '|',
     * có xử lý bỏ qua ký tự escape '\|' và loại bỏ dấu '|' ở rìa ngoài cùng.
     */
    private static List<String> splitTableCells(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|") && !trimmed.endsWith("\\|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int idx = 0; idx < trimmed.length(); idx++) {
            char c = trimmed.charAt(idx);
            if (c == '\\' && idx + 1 < trimmed.length() && trimmed.charAt(idx + 1) == '|') {
                current.append('|');
                idx++;
            } else if (c == '|') {
                cells.add(cleanTableCell(current.toString()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(cleanTableCell(current.toString()));
        return cells;
    }

    /**
     * Làm sạch nội dung ô trong bảng và chuyển thẻ HTML <br> thành ký tự xuống dòng '\n'.
     */
    private static String cleanTableCell(String rawCell) {
        return rawCell.trim().replaceAll("(?i)<br\\s*/?>", "\n");
    }

    /**
     * Xác định cờ căn lề (Gravity) cho cột dựa trên ký hiệu dấu hai chấm ':' ở dòng phân cách bảng (:---:, ---:, :---).
     */
    private static int parseAlignment(String sepCell) {
        String s = sepCell.trim();
        boolean left = s.startsWith(":");
        boolean right = s.endsWith(":");
        if (left && right) {
            return Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
        }
        if (right) {
            return Gravity.END | Gravity.CENTER_VERTICAL;
        }
        return Gravity.START | Gravity.CENTER_VERTICAL;
    }

    /**
     * Dựng View bảng dữ liệu hoàn chỉnh bọc trong HorizontalScrollView (hỗ trợ cuộn ngang khi bảng rộng).
     * Thực hiện đo kích thước 2 lượt (2-pass measurement) để đồng bộ độ rộng từng cột và chiều cao từng hàng.
     */
    private static View createTableView(Context ctx, List<String> tableLines) {
        if (tableLines.size() < 2) {
            return null;
        }
        List<String> headers = splitTableCells(tableLines.get(0));
        List<String> separators = splitTableCells(tableLines.get(1));
        int colCount = headers.size();
        if (colCount == 0) {
            return null;
        }
        int[] alignments = new int[colCount];
        for (int c = 0; c < colCount; c++) {
            alignments[c] = c < separators.size()
                    ? parseAlignment(separators.get(c))
                    : (Gravity.START | Gravity.CENTER_VERTICAL);
        }

        List<List<String>> rows = new ArrayList<>();
        rows.add(headers);
        for (int r = 2; r < tableLines.size(); r++) {
            rows.add(splitTableCells(tableLines.get(r)));
        }

        int rowCount = rows.size();
        int padH = dp(ctx, 10);
        int padV = dp(ctx, 8);
        int maxColWidth = dp(ctx, 210);
        int minColWidth = dp(ctx, 48);
        int dividerWidth = Math.max(1, dp(ctx, 1));
        int minRowHeight = dp(ctx, 34);

        TextView[][] cellViews = new TextView[rowCount][colCount];
        for (int r = 0; r < rowCount; r++) {
            boolean isHeader = (r == 0);
            List<String> rowCells = rows.get(r);
            for (int c = 0; c < colCount; c++) {
                String cellText = c < rowCells.size() ? rowCells.get(c) : "";
                cellViews[r][c] = createTableCellView(ctx, cellText, alignments[c], isHeader, padH, padV, maxColWidth);
            }
        }

        // Pass 1: measure natural width of every column across header + all body rows
        int[] colWidths = new int[colCount];
        int atMostWidthSpec = View.MeasureSpec.makeMeasureSpec(maxColWidth, View.MeasureSpec.AT_MOST);
        int unspecHeightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int totalColsWidth = 0;
        for (int c = 0; c < colCount; c++) {
            int maxW = minColWidth;
            for (int r = 0; r < rowCount; r++) {
                TextView tv = cellViews[r][c];
                tv.measure(atMostWidthSpec, unspecHeightSpec);
                maxW = Math.max(maxW, Math.min(maxColWidth, tv.getMeasuredWidth()));
            }
            colWidths[c] = maxW;
            totalColsWidth += maxW;
        }

        int totalDividersWidth = Math.max(0, colCount - 1) * dividerWidth;
        int targetBubbleInnerWidth = Math.max(
                dp(ctx, 200),
                ctx.getResources().getDisplayMetrics().widthPixels - dp(ctx, 108));
        // Nếu tổng bề rộng các cột nhỏ hơn khung bong bóng chat thì phân bổ đều phần dư cho các cột để bảng giãn đều đẹp mắt
        if (totalColsWidth + totalDividersWidth < targetBubbleInnerWidth) {
            int extra = targetBubbleInnerWidth - (totalColsWidth + totalDividersWidth);
            int perCol = extra / colCount;
            int remainder = extra % colCount;
            for (int c = 0; c < colCount; c++) {
                colWidths[c] += perCol + (c == colCount - 1 ? remainder : 0);
            }
            totalColsWidth += extra;
        }
        int totalTableWidth = totalColsWidth + totalDividersWidth;

        // Pass 2: lay out explicit-sized LinearLayout rows (avoids TableLayout/TableRow MATCH_PARENT height bugs)
        LinearLayout tableColumn = new LinearLayout(ctx);
        tableColumn.setOrientation(LinearLayout.VERTICAL);
        tableColumn.setLayoutParams(new HorizontalScrollView.LayoutParams(
                totalTableWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        int borderColor = ContextCompat.getColor(ctx, R.color.border);
        int borderStrongColor = ContextCompat.getColor(ctx, R.color.border_strong);
        int headerBg = ContextCompat.getColor(ctx, R.color.primary_50);
        int evenBg = ContextCompat.getColor(ctx, R.color.surface);
        int oddBg = ContextCompat.getColor(ctx, R.color.surface_container_low);

        for (int r = 0; r < rowCount; r++) {
            boolean isHeader = (r == 0);
            boolean isAltRow = !isHeader && ((r - 1) % 2 == 1);

            int rowHeight = minRowHeight;
            for (int c = 0; c < colCount; c++) {
                TextView tv = cellViews[r][c];
                int exactWSpec = View.MeasureSpec.makeMeasureSpec(colWidths[c], View.MeasureSpec.EXACTLY);
                tv.measure(exactWSpec, unspecHeightSpec);
                rowHeight = Math.max(rowHeight, tv.getMeasuredHeight());
            }

            LinearLayout rowLayout = new LinearLayout(ctx);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setBaselineAligned(false);
            rowLayout.setBackgroundColor(isHeader ? headerBg : (isAltRow ? oddBg : evenBg));
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(totalTableWidth, rowHeight));

            for (int c = 0; c < colCount; c++) {
                rowLayout.addView(cellViews[r][c], new LinearLayout.LayoutParams(colWidths[c], rowHeight));
                if (c < colCount - 1) {
                    View colDivider = new View(ctx);
                    colDivider.setBackgroundColor(borderColor);
                    rowLayout.addView(colDivider, new LinearLayout.LayoutParams(dividerWidth, rowHeight));
                }
            }

            tableColumn.addView(rowLayout);

            if (r < rowCount - 1) {
                View rowSep = new View(ctx);
                rowSep.setBackgroundColor(isHeader ? borderStrongColor : borderColor);
                tableColumn.addView(rowSep, new LinearLayout.LayoutParams(totalTableWidth, dividerWidth));
            }
        }

        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(true);
        hsv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        hsv.addView(tableColumn);

        // Rounded outer card + foreground border so scrolled content clips cleanly inside corners
        LinearLayout cardWrapper = new LinearLayout(ctx);
        cardWrapper.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(evenBg);
        cardBg.setCornerRadius(dp(ctx, 10));
        cardWrapper.setBackground(cardBg);

        GradientDrawable cardBorder = new GradientDrawable();
        cardBorder.setColor(Color.TRANSPARENT);
        cardBorder.setCornerRadius(dp(ctx, 10));
        cardBorder.setStroke(dp(ctx, 1), borderStrongColor);
        cardWrapper.setForeground(cardBorder);
        cardWrapper.setClipToOutline(true);
        cardWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cardWrapper.addView(hsv);
        return cardWrapper;
    }

    /**
     * Tạo TextView cho từng ô (cell) trong bảng với định dạng in đậm cho hàng tiêu đề (header) và hỗ trợ inline Markdown.
     */
    private static TextView createTableCellView(
            Context ctx,
            String cellText,
            int gravity,
            boolean isHeader,
            int padH,
            int padV,
            int maxColWidth) {
        TextView tv = new TextView(ctx);
        tv.setPadding(padH, padV, padH, padV);
        tv.setMaxWidth(maxColWidth);
        tv.setGravity(gravity);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tv.setLineSpacing(dp(ctx, 2), 1.0f);
        if (isHeader) {
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(ContextCompat.getColor(ctx, R.color.slate_900));
        } else {
            tv.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
        }
        tv.setText(formatInlineText(ctx, cellText));
        return tv;
    }

    /**
     * Tạo TextView cho dòng tiêu đề (Heading H1 -> H4) với kích thước chữ và màu sắc phân cấp theo level.
     */
    private static TextView createHeadingView(Context ctx, int level, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(formatInlineText(ctx, text));
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextIsSelectable(true);
        if (level == 1) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            tv.setTextColor(ContextCompat.getColor(ctx, R.color.on_primary_container));
        } else if (level == 2) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            tv.setTextColor(ContextCompat.getColor(ctx, R.color.primary_dark));
        } else {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            tv.setTextColor(ContextCompat.getColor(ctx, R.color.slate_900));
        }
        tv.setLineSpacing(dp(ctx, 2), 1.0f);
        return tv;
    }

    /**
     * Tạo đường kẻ ngang phân cách (Horizontal Rule: --- hoặc ***).
     */
    private static View createDividerView(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(ContextCompat.getColor(ctx, R.color.border_strong));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1)));
        return v;
    }

    /**
     * Tạo khối trích dẫn (Blockquote: > ...) có thanh dọc màu primary bên trái và khung bo góc.
     */
    private static View createQuoteView(Context ctx, String text) {
        LinearLayout quoteBox = new LinearLayout(ctx);
        quoteBox.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ContextCompat.getColor(ctx, R.color.surface));
        bg.setCornerRadius(dp(ctx, 8));
        bg.setStroke(dp(ctx, 1), ContextCompat.getColor(ctx, R.color.border));
        quoteBox.setBackground(bg);

        View bar = new View(ctx);
        bar.setBackgroundColor(ContextCompat.getColor(ctx, R.color.primary));
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                dp(ctx, 3), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView tv = new TextView(ctx);
        tv.setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8));
        tv.setText(formatInlineText(ctx, text));
        tv.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        tv.setLineSpacing(dp(ctx, 2), 1.0f);
        tv.setTextIsSelectable(true);

        quoteBox.addView(bar);
        quoteBox.addView(tv, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return quoteBox;
    }

    /**
     * Tạo khung hiển thị đoạn mã nguồn (Fenced Code Block: ``` ... ```) với nền tối, font monospace và cuộn ngang.
     */
    private static View createCodeBlockView(Context ctx, String code) {
        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ContextCompat.getColor(ctx, R.color.slate_900));
        bg.setCornerRadius(dp(ctx, 8));
        hsv.setBackground(bg);

        TextView tv = new TextView(ctx);
        tv.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));
        tv.setText(code);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(Color.parseColor("#E2E8F0"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setTextIsSelectable(true);
        hsv.addView(tv);
        return hsv;
    }

    /**
     * Tạo một dòng trong danh sách (Bullet hoặc đánh số thứ tự) có hỗ trợ thụt lề theo cấp độ (indentLevel).
     */
    private static View createListItemView(Context ctx, String marker, String body, int indentLevel) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int indentPx = dp(ctx, indentLevel * 12);
        row.setPadding(indentPx, dp(ctx, 2), 0, dp(ctx, 2));

        TextView tvMarker = new TextView(ctx);
        tvMarker.setText(marker);
        tvMarker.setTypeface(null, Typeface.BOLD);
        tvMarker.setTextColor(ContextCompat.getColor(ctx, R.color.primary));
        tvMarker.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        LinearLayout.LayoutParams markerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        markerLp.rightMargin = dp(ctx, 8);
        row.addView(tvMarker, markerLp);

        TextView tvBody = createParagraphView(ctx, body);
        row.addView(tvBody, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /**
     * Tạo TextView cho đoạn văn bản thường, kích hoạt bấm link (LinkMovementMethod) nếu có chứa URLSpan.
     */
    private static TextView createParagraphView(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        SpannableStringBuilder formatted = formatInlineText(ctx, text);
        tv.setText(formatted);
        tv.setTextColor(ContextCompat.getColor(ctx, R.color.chat_bot_text));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        tv.setLineSpacing(dp(ctx, 2.5f), 1.0f);

        URLSpan[] links = formatted.getSpans(0, formatted.length(), URLSpan.class);
        if (links != null && links.length > 0) {
            tv.setMovementMethod(LinkMovementMethod.getInstance());
            tv.setLinkTextColor(ContextCompat.getColor(ctx, R.color.text_link));
        } else {
            tv.setTextIsSelectable(true);
        }
        return tv;
    }

    /**
     * Phân tích cú pháp Markdown nội dòng (inline): `code`, [label](url), **bold**, __bold__, ~~strike~~, *italic*, _italic_
     * và trả về SpannableStringBuilder đã gắn các Span định dạng tương ứng.
     */
    public static SpannableStringBuilder formatInlineText(Context ctx, String raw) {
        if (raw == null || raw.isEmpty()) {
            return new SpannableStringBuilder("");
        }

        // First pass: extract `inline code` and [label](url) into spans while preserving remaining text
        SpannableStringBuilder sb = new SpannableStringBuilder();
        List<int[]> protectedRanges = new ArrayList<>();
        Matcher tokenMatcher = INLINE_LINK_OR_CODE_PATTERN.matcher(raw);
        int cursor = 0;

        while (tokenMatcher.find()) {
            if (tokenMatcher.start() > cursor) {
                sb.append(raw, cursor, tokenMatcher.start());
            }
            String token = tokenMatcher.group(1);
            int spanStart = sb.length();
            if (token.startsWith("`") && token.endsWith("`") && token.length() >= 2) {
                String codeText = token.substring(1, token.length() - 1);
                sb.append(codeText);
                int spanEnd = sb.length();
                sb.setSpan(new TypefaceSpan("monospace"), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new RelativeSizeSpan(0.92f), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new BackgroundColorSpan(ContextCompat.getColor(ctx, R.color.primary_50)),
                        spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.on_primary_container)),
                        spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                protectedRanges.add(new int[]{spanStart, spanEnd});
            } else {
                int split = token.indexOf("](");
                String label = split > 1 ? token.substring(1, split) : token;
                String url = tokenMatcher.group(2);
                sb.append(label);
                int spanEnd = sb.length();
                if (url != null && !url.isEmpty()) {
                    sb.setSpan(new URLSpan(url), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                protectedRanges.add(new int[]{spanStart, spanEnd});
            }
            cursor = tokenMatcher.end();
        }
        if (cursor < raw.length()) {
            sb.append(raw, cursor, raw.length());
        }

        // Second pass: emphasis formatting on non-code segments
        applyWrappedSpan(sb, BOLD_STAR_PATTERN, protectedRanges,
                () -> new StyleSpan(Typeface.BOLD),
                () -> new ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.slate_900)));
        applyWrappedSpan(sb, BOLD_UNDER_PATTERN, protectedRanges,
                () -> new StyleSpan(Typeface.BOLD),
                () -> new ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.slate_900)));
        applyWrappedSpan(sb, STRIKE_PATTERN, protectedRanges, StrikethroughSpan::new, null);
        applyWrappedSpan(sb, ITALIC_STAR_PATTERN, protectedRanges, () -> new StyleSpan(Typeface.ITALIC), null);
        applyWrappedSpan(sb, ITALIC_UNDER_PATTERN, protectedRanges, () -> new StyleSpan(Typeface.ITALIC), null);

        return sb;
    }

    private interface SpanSupplier {
        Object create();
    }

    /**
     * Tìm các cặp ký tự bọc định dạng (như **, __, ~~, *), xóa ký tự đánh dấu khỏi chuỗi hiển thị
     * và áp dụng Span kiểu chữ lên vùng văn bản bên trong (bỏ qua các vùng nằm trong `inline code` hoặc URL).
     */
    private static void applyWrappedSpan(
            SpannableStringBuilder sb,
            Pattern pattern,
            List<int[]> protectedRanges,
            SpanSupplier primarySpan,
            SpanSupplier secondarySpan) {
        Matcher m = pattern.matcher(sb);
        List<int[]> matches = new ArrayList<>();
        while (m.find()) {
            if (!isInsideProtected(m.start(), protectedRanges)
                    && !isInsideProtected(m.end() - 1, protectedRanges)) {
                matches.add(new int[]{m.start(), m.end(), m.start(1), m.end(1)});
            }
        }
        for (int idx = matches.size() - 1; idx >= 0; idx--) {
            int[] pos = matches.get(idx);
            int fullStart = pos[0];
            int fullEnd = pos[1];
            int innerStart = pos[2];
            int innerEnd = pos[3];
            int suffixLen = fullEnd - innerEnd;
            int prefixLen = innerStart - fullStart;
            int removed = prefixLen + suffixLen;

            sb.delete(innerEnd, fullEnd);
            sb.delete(fullStart, innerStart);
            int newEnd = fullStart + (innerEnd - innerStart);
            sb.setSpan(primarySpan.create(), fullStart, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (secondarySpan != null) {
                sb.setSpan(secondarySpan.create(), fullStart, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            shiftProtectedRanges(protectedRanges, fullStart, removed);
        }
    }

    /**
     * Kiểm tra một vị trí ký tự có nằm trong vùng bảo vệ (inline code / link) không để tránh format nhầm dấu * hoặc _.
     */
    private static boolean isInsideProtected(int index, List<int[]> protectedRanges) {
        for (int[] range : protectedRanges) {
            if (index >= range[0] && index < range[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dịch chuyển lại tọa độ các vùng bảo vệ (protectedRanges) sau khi xóa các ký tự cú pháp Markdown phía trước.
     */
    private static void shiftProtectedRanges(List<int[]> protectedRanges, int editStart, int delta) {
        if (delta == 0) {
            return;
        }
        for (int[] range : protectedRanges) {
            if (range[0] > editStart) {
                range[0] = Math.max(editStart, range[0] - delta);
                range[1] = Math.max(range[0], range[1] - delta);
            }
        }
    }

    /**
     * Chuyển đổi đơn vị kích thước từ dp (density-independent pixels) sang pixel thực tế của màn hình thiết bị.
     */
    private static int dp(Context ctx, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, ctx.getResources().getDisplayMetrics()));
    }
}
