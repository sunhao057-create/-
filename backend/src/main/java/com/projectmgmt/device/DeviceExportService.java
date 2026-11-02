package com.projectmgmt.device;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.profile.FileStorageService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceExportService {

    private static final Logger log = LoggerFactory.getLogger(DeviceExportService.class);
    private static final int PHOTO_COL = 8;
    private static final int QR_COL = 9;
    private static final int IMG_PX = 72;
    private static final int QR_PX = 80;

    private final DeviceService deviceService;
    private final DeviceQrCode deviceQrCode;
    private final FileStorageService fileStorageService;

    public ResponseEntity<byte[]> workbook(DeviceQuery query, List<Long> ids) {
        List<DeviceView> rows = deviceService.list(query, ids);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(output, rows);
        String filename = "设备台账-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, java.nio.charset.StandardCharsets.UTF_8).build().toString()
                )
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(output.toByteArray());
    }

    private void write(OutputStream output, List<DeviceView> rows) {
        ImageIO.setUseCache(false);
        Map<String, byte[]> thumbs = new HashMap<>();
        byte[][] qrs = qrJpegs(rows);
        SXSSFWorkbook workbook = new SXSSFWorkbook(50);
        workbook.setCompressTempFiles(true);
        try (workbook) {
            Sheet sheet = workbook.createSheet("设备台账");
            CellStyle wrap = workbook.createCellStyle();
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(VerticalAlignment.CENTER);
            Row header = sheet.createRow(0);
            String[] titles = {"编码", "设备名称", "型号", "序列号", "放置位置", "所属部门", "状态", "说明", "设备照片", "设备二维码"};
            for (int i = 0; i < titles.length; i++) {
                header.createCell(i).setCellValue(titles[i]);
                sheet.setColumnWidth(i, i >= PHOTO_COL ? 16 * 256 : 16 * 256);
            }
            header.setHeightInPoints(22);
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            CreationHelper helper = workbook.getCreationHelper();
            for (int i = 0; i < rows.size(); i++) {
                DeviceView item = rows.get(i);
                int rowIndex = i + 1;
                Row row = sheet.createRow(rowIndex);
                row.setHeightInPoints(64);
                String[] values = {
                        item.code(),
                        item.name(),
                        item.model(),
                        item.serialNo(),
                        item.location(),
                        item.departmentName(),
                        item.statusLabel(),
                        item.remark()
                };
                for (int c = 0; c < values.length; c++) {
                    var cell = row.createCell(c);
                    cell.setCellValue(values[c] == null ? "" : values[c]);
                    cell.setCellStyle(wrap);
                }
                int picture = pictureForPhoto(workbook, item.photoUrl(), thumbs);
                if (picture >= 0) {
                    embed(helper, drawing, rowIndex, PHOTO_COL, picture);
                }
                if (qrs[i] != null && qrs[i].length > 0) {
                    embed(helper, drawing, rowIndex, QR_COL, workbook.addPicture(qrs[i], Workbook.PICTURE_TYPE_JPEG));
                }
            }
            workbook.write(output);
            output.flush();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("设备台账导出失败", ex);
            throw ApiException.badRequest("导出失败");
        }
    }

    private byte[][] qrJpegs(List<DeviceView> rows) {
        byte[][] result = new byte[rows.size()][];
        if (rows.isEmpty()) {
            return result;
        }
        int threads = Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<byte[]>> futures = new ArrayList<>(rows.size());
            for (DeviceView item : rows) {
                futures.add(pool.submit(() -> deviceQrCode.jpeg(item, QR_PX)));
            }
            for (int i = 0; i < futures.size(); i++) {
                result[i] = futures.get(i).get();
            }
            return result;
        } catch (ApiException ex) {
            throw ex;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof ApiException api) {
                throw api;
            }
            log.error("生成导出二维码失败", cause == null ? ex : cause);
            throw ApiException.badRequest("导出失败");
        } catch (Exception ex) {
            log.error("生成导出二维码失败", ex);
            throw ApiException.badRequest("导出失败");
        } finally {
            pool.shutdownNow();
        }
    }

    private int pictureForPhoto(Workbook workbook, String photoUrl, Map<String, byte[]> thumbs) {
        String key = photoUrl == null ? "" : photoUrl.trim();
        byte[] bytes = thumbs.computeIfAbsent(key, url -> {
            byte[] thumb = thumbnailJpeg(url);
            return thumb == null ? new byte[0] : thumb;
        });
        if (bytes.length == 0) {
            return -1;
        }
        return workbook.addPicture(bytes, Workbook.PICTURE_TYPE_JPEG);
    }

    private byte[] thumbnailJpeg(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            return null;
        }
        Path path = fileStorageService.resolveUpload(photoUrl);
        if (path == null) {
            log.warn("导出台账跳过照片（文件不存在）: {}", photoUrl);
            return null;
        }
        try {
            BufferedImage source = readImage(path);
            if (source == null) {
                log.warn("导出台账跳过照片（无法解码）: {}", photoUrl);
                return null;
            }
            BufferedImage dest = new BufferedImage(IMG_PX, IMG_PX, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dest.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, IMG_PX, IMG_PX);
            double scale = Math.min((double) IMG_PX / Math.max(source.getWidth(), 1), (double) IMG_PX / Math.max(source.getHeight(), 1));
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            g.drawImage(source, (IMG_PX - width) / 2, (IMG_PX - height) / 2, width, height, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.55f);
            try (var ios = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(dest, null, null), param);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (Exception ex) {
            log.warn("导出台账跳过照片 {}: {}", photoUrl, ex.toString());
            return null;
        }
    }

    private BufferedImage readImage(Path path) {
        try (InputStream in = Files.newInputStream(path); ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis != null) {
                var readers = ImageIO.getImageReaders(iis);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(iis, true, true);
                        int width = Math.max(reader.getWidth(0), 1);
                        int height = Math.max(reader.getHeight(0), 1);
                        int subsample = Math.max(1, Math.max(width, height) / (IMG_PX * 4));
                        ImageReadParam param = reader.getDefaultReadParam();
                        if (subsample > 1) {
                            param.setSourceSubsampling(subsample, subsample, 0, 0);
                        }
                        BufferedImage image = reader.read(0, param);
                        if (image != null) {
                            return image;
                        }
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through to ImageIO.read
        }
        try (InputStream in = Files.newInputStream(path)) {
            return ImageIO.read(in);
        } catch (Exception ex) {
            return null;
        }
    }

    private void embed(CreationHelper helper, Drawing<?> drawing, int rowIndex, int colIndex, int picture) {
        ClientAnchor anchor = helper.createClientAnchor();
        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
        anchor.setCol1(colIndex);
        anchor.setRow1(rowIndex);
        anchor.setCol2(colIndex + 1);
        anchor.setRow2(rowIndex + 1);
        anchor.setDx1(0);
        anchor.setDy1(0);
        anchor.setDx2(0);
        anchor.setDy2(0);
        drawing.createPicture(anchor, picture);
    }
}
