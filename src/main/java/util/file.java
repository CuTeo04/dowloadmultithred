package util;

import java.io.File;
import java.net.HttpURLConnection;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.Tika;

public class file {
	private static final DecimalFormat df = new DecimalFormat("#.##");

	public static String formatFileSize(long size) {
		String[] units = { "B", "KB", "MB", "GB", "TB" };
		int unitIndex = 0;
		double fileSize = size;

		while (fileSize > 1024 && unitIndex < units.length - 1) {
			fileSize /= 1024;
			unitIndex++;
		}
		return df.format(fileSize) + " " + units[unitIndex];
	}
	
	public static String getFileName(HttpURLConnection connection, String fileUrl) {
		String fileName = null;
		Tika tika = new Tika();

		// Lấy thông tin từ Content-Disposition
		String disposition = connection.getHeaderField("Content-Disposition");
		if (disposition != null && disposition.contains("filename=")) {
			Pattern pattern = Pattern.compile("filename=[\"']?([^\"']+)[\"']?");
			Matcher matcher = pattern.matcher(disposition);
			if (matcher.find()) {
				fileName = matcher.group(1);
			}
		}

		// Nếu không có tên file từ Content-Disposition, lấy từ URL
		if (fileName == null) {
			String path = new File(fileUrl).getName();
			int queryIndex = path.indexOf('?');
			if (queryIndex > 0) {
				path = path.substring(0, queryIndex);
			}
			if (!path.isEmpty()) {
				fileName = path;
			}
		}

		// Nếu tên file vẫn chưa xác định, sử dụng Apache Tika để nhận diện kiểu file
		if (fileName == null || fileName.trim().isEmpty()) {
			fileName = "downloaded_file";
			String contentType = connection.getContentType();
			if (contentType != null) {
				String extension = tika.detect(contentType);
				if (extension != null && !extension.isEmpty()) {
					fileName += extension; // Thêm phần mở rộng vào tên file
				}
			}
		}

		// Chắc chắn rằng tên file không chứa ký tự không hợp lệ
		return sanitizeFileName(fileName);
	}
	
	private static String sanitizeFileName(String fileName) {
		return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
	}
	

}
