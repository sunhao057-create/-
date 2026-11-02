package com.projectmgmt.device;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.projectmgmt.common.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class DeviceQrCode {

    public String payload(Device device) {
        return payload(
                device.getCode(),
                device.getName(),
                device.getModel(),
                device.getSerialNo(),
                device.getLocation(),
                device.getDepartment() == null ? "" : device.getDepartment().getName()
        );
    }

    public byte[] png(Device device, int size) {
        return encodePng(payload(device), size, ErrorCorrectionLevel.M);
    }

    public byte[] png(DeviceView item, int size) {
        return encodePng(viewPayload(item), size, ErrorCorrectionLevel.M);
    }

    public byte[] jpeg(DeviceView item, int size) {
        try {
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix(viewPayload(item), size, ErrorCorrectionLevel.L));
            if (image.getType() != BufferedImage.TYPE_INT_RGB) {
                BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                g.drawImage(image, 0, 0, null);
                g.dispose();
                image = rgb;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "jpeg", out)) {
                throw new IllegalStateException("jpeg");
            }
            return out.toByteArray();
        } catch (Exception ex) {
            throw ApiException.badRequest("生成二维码失败");
        }
    }

    private String viewPayload(DeviceView item) {
        return payload(
                item.code(),
                item.name(),
                item.model(),
                item.serialNo(),
                item.location(),
                item.departmentName()
        );
    }

    private String payload(String code, String name, String model, String serialNo, String location, String department) {
        return String.join("\n",
                "设备编码：" + nullToEmpty(code),
                "设备名称：" + nullToEmpty(name),
                "型号：" + nullToEmpty(model),
                "序列号：" + nullToEmpty(serialNo),
                "放置位置：" + nullToEmpty(location),
                "所属部门：" + nullToEmpty(department)
        );
    }

    private byte[] encodePng(String payload, int size, ErrorCorrectionLevel level) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix(payload, size, level), "PNG", out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw ApiException.badRequest("生成二维码失败");
        }
    }

    private BitMatrix matrix(String payload, int size, ErrorCorrectionLevel level) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.ERROR_CORRECTION, level);
        hints.put(EncodeHintType.MARGIN, 1);
        return new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
