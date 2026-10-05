package com.feloguarin.lensguard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.media.ExifInterface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A printable inspection report. It repeats the app's limits on the first page so the document
 * cannot be read as a certification.
 */
@SuppressLint("ExifInterface") // The AndroidX copy backports fixes this app's minSdk 28 already has.
final class ReportPdf {
    private static final int PAGE_WIDTH = 595, PAGE_HEIGHT = 842; // A4 in PostScript points.
    private static final int MARGIN = 44, FOOTER = 28;
    private static final int CONTENT = PAGE_WIDTH - 2 * MARGIN;
    private static final int INK = Color.rgb(20, 27, 30), SOFT = Color.rgb(90, 104, 108);
    private static final int ACCENT = Color.rgb(58, 104, 30), CAUTION = Color.rgb(138, 90, 0);
    private static final int PHOTO_MAX_HEIGHT = 230, PHOTO_PIXELS = 640;
    private static final int PAGE_SPACE = PAGE_HEIGHT - 2 * MARGIN - FOOTER;
    /** The full note stays in the JSON report; the PDF keeps each observation readable on a page. */
    private static final int NOTE_LIMIT = 1500, LEAD_LIMIT = 25;

    private interface Block {
        int height();
        void draw(Canvas canvas, int top);
    }

    private ReportPdf() {}

    static File write(Context context, Inspection inspection, boolean includeAddresses, InspectionStore store)
            throws IOException {
        File file = ReportBuilder.newReportFile(context, "pdf");
        List<Block> blocks = blocks(context, inspection, includeAddresses, store);
        List<List<int[]>> pages = paginate(blocks); // {block index, top}
        PdfDocument document = new PdfDocument();
        try {
            String footer = context.getString(R.string.pdf_footer, BuildConfig.VERSION_NAME, inspection.title);
            for (int p = 0; p < pages.size(); p++) {
                PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, p + 1).create());
                Canvas canvas = page.getCanvas();
                for (int[] placement : pages.get(p)) blocks.get(placement[0]).draw(canvas, placement[1]);
                TextPaint small = paint(8, SOFT, false);
                canvas.drawText(footer, MARGIN, PAGE_HEIGHT - MARGIN / 2f, small);
                String number = context.getString(R.string.pdf_page, p + 1, pages.size());
                canvas.drawText(number, PAGE_WIDTH - MARGIN - small.measureText(number), PAGE_HEIGHT - MARGIN / 2f, small);
                document.finishPage(page);
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                document.writeTo(out);
            }
        } finally {
            document.close();
        }
        return file;
    }

    private static List<List<int[]>> paginate(List<Block> blocks) {
        List<List<int[]>> pages = new ArrayList<>();
        List<int[]> page = new ArrayList<>();
        int y = MARGIN, bottom = MARGIN + PAGE_SPACE;
        for (int i = 0; i < blocks.size(); i++) {
            int height = blocks.get(i).height();
            if (y + height > bottom && !page.isEmpty()) {
                pages.add(page);
                page = new ArrayList<>();
                y = MARGIN;
            }
            page.add(new int[] {i, y});
            y += height;
        }
        pages.add(page);
        return pages;
    }

    private static List<Block> blocks(Context context, Inspection inspection, boolean includeAddresses, InspectionStore store) {
        List<Block> blocks = new ArrayList<>();
        DateFormat dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        blocks.add(text(context.getString(R.string.pdf_title), 20, INK, true, 4));
        blocks.add(text(inspection.title, 14, ACCENT, true, 6));
        blocks.add(text(context.getString(R.string.pdf_meta, dates.format(new Date(inspection.createdAt)),
                dates.format(new Date()), BuildConfig.VERSION_NAME, Build.MANUFACTURER + " " + Build.MODEL,
                Build.VERSION.RELEASE), 9, SOFT, false, 4));
        blocks.add(text(context.getString(includeAddresses ? R.string.pdf_addresses_included : R.string.pdf_addresses_masked),
                9, SOFT, false, 12));
        blocks.add(caution(context.getString(R.string.pdf_caution)));

        int inspected = inspection.count(Inspection.INSPECTED) + inspection.count(Inspection.CLOSER_LOOK);
        blocks.add(heading(context.getString(R.string.pdf_summary)));
        blocks.add(text(context.getString(R.string.pdf_summary_text, inspected, Checklist.SPOTS.length,
                inspection.count(Inspection.CLOSER_LOOK), inspection.observations.size(), inspection.photoCount()),
                10, INK, false, 10));

        blocks.add(heading(context.getString(R.string.pdf_checklist)));
        for (Checklist.Spot spot : Checklist.SPOTS) {
            String status = inspection.status(spot.id);
            int label = Inspection.INSPECTED.equals(status) ? R.string.status_inspected
                    : Inspection.CLOSER_LOOK.equals(status) ? R.string.status_closer_look : R.string.status_not_inspected;
            blocks.add(text("•  " + context.getString(spot.title) + " — " + context.getString(label), 10,
                    Inspection.CLOSER_LOOK.equals(status) ? CAUTION : INK, false, 2));
        }
        blocks.add(spacer(10));

        blocks.add(heading(context.getString(R.string.pdf_observations)));
        if (inspection.observations.isEmpty()) blocks.add(text(context.getString(R.string.pdf_no_observations), 10, SOFT, false, 8));
        int number = 0;
        for (Observation observation : inspection.observations) {
            number++;
            Checklist.Spot spot = Checklist.find(observation.spot);
            String header = context.getString(R.string.pdf_observation_header, number, sourceName(context, observation.source),
                    dates.format(new Date(observation.time)));
            if (spot != null) header += " · " + context.getString(spot.title);
            List<Block> parts = new ArrayList<>();
            parts.add(text(header, 10, ACCENT, true, 3));
            if (observation.summary != null) parts.add(text(observation.summary, 10, INK, false, 3));
            if (observation.note != null && !observation.note.isEmpty()) {
                String note = observation.note.length() > NOTE_LIMIT
                        ? observation.note.substring(0, NOTE_LIMIT) + "…" : observation.note;
                parts.add(text(context.getString(R.string.pdf_note, note), 10, INK, false, 3));
            }
            String leads = leads(context, observation, includeAddresses);
            if (leads != null) parts.add(text(leads, 9, CAUTION, false, 3));
            File photo = store.photoFile(observation.photo);
            if (photo != null && photo.exists()) {
                Block image = photo(photo);
                if (image != null) parts.add(image);
            }
            parts.add(spacer(12));
            Block group = group(parts);
            if (group.height() <= PAGE_SPACE) blocks.add(group);
            else blocks.addAll(parts);
        }
        blocks.add(heading(context.getString(R.string.pdf_method)));
        blocks.add(text(context.getString(R.string.pdf_method_text), 9, SOFT, false, 0));
        return blocks;
    }

    /** Leads from a saved nearby scan, which a reader most needs to follow up. */
    private static String leads(Context context, Observation observation, boolean includeAddresses) {
        if (!Observation.NEARBY.equals(observation.source) || observation.data == null) return null;
        StringBuilder text = new StringBuilder();
        int count = 0;
        for (String group : new String[] {"network", "bluetooth", "wifi"}) {
            JSONArray devices = observation.data.optJSONArray(group);
            if (devices == null) continue;
            for (int i = 0; i < devices.length(); i++) {
                JSONObject device = devices.optJSONObject(i);
                if (device == null) continue;
                JSONArray hints = device.optJSONArray("hints");
                if (hints == null || hints.length() == 0) continue;
                if (++count > LEAD_LIMIT) {
                    text.append("\n…");
                    return text.toString();
                }
                String address = device.optString("address", "");
                if (!includeAddresses && !address.isEmpty()) address = Text.maskAddress(address);
                text.append(text.length() == 0 ? context.getString(R.string.pdf_leads) : "").append("\n•  ")
                        .append(device.optString("name", context.getString(R.string.pdf_unnamed)));
                if (!address.isEmpty()) text.append(" (").append(address).append(')');
                for (int h = 0; h < hints.length(); h++) text.append(" · ").append(hintName(context, hints.optString(h)));
            }
        }
        return text.length() == 0 ? null : text.toString();
    }

    private static String hintName(Context context, String hint) {
        if (WirelessProbe.HINT_VIDEO_SERVICE.equals(hint)) return context.getString(R.string.hint_video_service);
        if (WirelessProbe.HINT_CAMERA_ROLE.equals(hint)) return context.getString(R.string.hint_camera_role);
        return context.getString(R.string.hint_camera_name);
    }

    private static String sourceName(Context context, String source) {
        switch (source) {
            case Observation.CAMERA: return context.getString(R.string.source_camera);
            case Observation.NEARBY: return context.getString(R.string.source_nearby);
            case Observation.MAGNETIC: return context.getString(R.string.source_magnetic);
            case Observation.SOUND: return context.getString(R.string.source_sound);
            default: return context.getString(R.string.source_note);
        }
    }

    private static TextPaint paint(float size, int color, boolean bold) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return paint;
    }

    private static Block text(String value, float size, int color, boolean bold, int after) {
        return text(value, size, color, bold, after, CONTENT, 0);
    }

    private static Block text(String value, float size, int color, boolean bold, int after, int width, int indent) {
        StaticLayout layout = StaticLayout.Builder.obtain(value, 0, value.length(), paint(size, color, bold), width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0, 1.15f).build();
        return new Block() {
            @Override public int height() { return layout.getHeight() + after; }
            @Override public void draw(Canvas canvas, int top) {
                canvas.save();
                canvas.translate(MARGIN + indent, top);
                layout.draw(canvas);
                canvas.restore();
            }
        };
    }

    private static Block heading(String value) {
        Block title = text(value, 13, INK, true, 6);
        return new Block() {
            @Override public int height() { return title.height() + 8; }
            @Override public void draw(Canvas canvas, int top) {
                Paint rule = new Paint();
                rule.setColor(ACCENT);
                canvas.drawRect(MARGIN, top, MARGIN + 28, top + 2, rule);
                title.draw(canvas, top + 8);
            }
        };
    }

    private static Block caution(String value) {
        Block inner = text(value, 10, CAUTION, false, 0, CONTENT - 24, 12);
        return new Block() {
            @Override public int height() { return inner.height() + 24 + 14; }
            @Override public void draw(Canvas canvas, int top) {
                Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
                border.setStyle(Paint.Style.STROKE);
                border.setStrokeWidth(1.2f);
                border.setColor(CAUTION);
                canvas.drawRoundRect(new RectF(MARGIN, top, MARGIN + CONTENT, top + inner.height() + 24), 6, 6, border);
                inner.draw(canvas, top + 12);
            }
        };
    }

    private static Block spacer(int height) {
        return new Block() {
            @Override public int height() { return height; }
            @Override public void draw(Canvas canvas, int top) {}
        };
    }

    /** Keeps an observation's parts on one page when they fit. */
    private static Block group(List<Block> parts) {
        return new Block() {
            @Override public int height() {
                int total = 0;
                for (Block part : parts) total += part.height();
                return total;
            }
            @Override public void draw(Canvas canvas, int top) {
                int y = top;
                for (Block part : parts) {
                    part.draw(canvas, y);
                    y += part.height();
                }
            }
        };
    }

    /** Sizes the photo from its header alone and decodes pixels only while drawing. */
    private static Block photo(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        boolean quarterTurn = quarterTurn(file);
        float width = quarterTurn ? bounds.outHeight : bounds.outWidth, height = quarterTurn ? bounds.outWidth : bounds.outHeight;
        float scale = Math.min((float) CONTENT / width, PHOTO_MAX_HEIGHT / height);
        int drawWidth = Math.round(width * scale), drawHeight = Math.round(height * scale);
        return new Block() {
            @Override public int height() { return drawHeight + 6; }
            @Override public void draw(Canvas canvas, int top) {
                Bitmap bitmap = Photos.decode(file, PHOTO_PIXELS);
                if (bitmap == null) return;
                canvas.drawBitmap(bitmap, null, new RectF(MARGIN, top, MARGIN + drawWidth, top + drawHeight),
                        new Paint(Paint.FILTER_BITMAP_FLAG));
                bitmap.recycle();
            }
        };
    }

    private static boolean quarterTurn(File file) {
        try {
            int orientation = new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            return orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270;
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }
}
