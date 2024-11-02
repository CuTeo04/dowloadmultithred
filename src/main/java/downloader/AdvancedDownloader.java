// executor: quản lí số luồng tối đa chạy cùng lúc = số luồng tải + luồng quan sát, áp dụng để hủy tất cả các luồng đang chạy
// lock: cho phép nhiều luồng truy cập đến cùng một tài nguyên một cách an toàn
// pauseCondition: cho phép điều khiển pause và resume các luồng
// start: startTime:thời gian bắt đầu tải, cờ bắt đầu tải 
// runningFlag: cờ cho phép pause và resume
// totalPauseTime: tổng thời gian tạm dừng
// lastPauseTime: thời điểm tạm dừng cuối cùng

package downloader; 

import java.io.*;
import java.net.*;
import java.text.DecimalFormat;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;

import javafx.application.Platform;
import javafx.scene.control.ProgressBar;

import org.apache.tika.Tika;


public class AdvancedDownloader {
	private static final int NUM_SEGMENTS = 4;
	private static final DecimalFormat df = new DecimalFormat("#.##");
	private static final int MAX_REDIRECTS = 5;
	private static final int TORRENT_UPLOAD_RATE_LIMIT = 0;
	private static final int TORRENT_DOWNLOAD_RATE_LIMIT = 0;
	
	private ProgressBar progressBar;   
	private javafx.scene.control.TextArea statusArea;  
	private volatile boolean runningFlag; 
    private volatile double totalPauseTime;
    private volatile double lastPauseTime;
    private volatile double startTime;
	private ExecutorService executor;
	private final ReentrantLock lock;
    private final Condition pauseCondition;
	

	public AdvancedDownloader(ProgressBar progressBar,
			javafx.scene.control.TextArea statusArea) {
		this.progressBar = progressBar;
		this.statusArea = statusArea;
		this.runningFlag=false;
		this.startTime=0;
		this.executor =Executors.newFixedThreadPool(NUM_SEGMENTS+1);
		this.lock= new ReentrantLock();  
		this.pauseCondition = lock.newCondition(); 
	}
	
	public void startDownload(String input) {  
	    this.runningFlag = true; 
	    this.startTime= getCurrentTime();
	    updateStatus("Start Downloading...");
	    try {
	        if (input.endsWith(".torrent")) {
	            downloadTorrent(input);
	        } else {
	            URL url = new URL(input);
	            String protocol = url.getProtocol().toLowerCase();
	            switch (protocol) {
	                case "http":
	                case "https":
	                    downloadDirectLink(input);
	                    break;
	                default:
	                    updateStatus("Unsupported protocol: " + protocol);
	            }
	        }
	    } catch (Exception e) {
	        updateStatus("Error occurred: " + e.getMessage());
	        e.printStackTrace();
	    } finally {
	        this.runningFlag = false;
	        executor.shutdown();
	    }
	}
	
	public void cancelDownload() {
	    try {
	        executor.shutdownNow(); 
	    } catch (Exception e) { 
	        e.printStackTrace(); 
	    }
	}

	
	public void pauseDownload() {
		 updateStatus("Paused...");
		this.lastPauseTime=getCurrentTime();
		this.runningFlag=false;
	}
	
	public void resumeDownload() {
		updateStatus("Resumed...");
		this.totalPauseTime += getCurrentTime() - this.lastPauseTime;
		this.runningFlag=true;
		lock.lock();
	    try {
	        pauseCondition.signalAll();
	    } finally {
	        lock.unlock();
	    }
	}
	
	public boolean getRunningFlag() {
		return this.runningFlag;
	}
	
	
	public boolean getStartStatus() {
		if (this.startTime==0) return 
			false; else return true;
	}
	private void downloadTorrent(String torrentPath) throws Exception {

		File torrentFile = new File(torrentPath);
		if (!torrentFile.exists()) {
			throw new FileNotFoundException("Torrent file not found");
		}

		File downloadDir = new File("downloads");
		if (!downloadDir.exists()) {
			downloadDir.mkdir();
		}

		SharedTorrent torrent = SharedTorrent.fromFile(torrentFile, downloadDir);
		torrent.setMaxUploadRate(TORRENT_UPLOAD_RATE_LIMIT);
		torrent.setMaxDownloadRate(TORRENT_DOWNLOAD_RATE_LIMIT);

		Client client = new Client(InetAddress.getLocalHost(), torrent);

		AtomicLong lastDownloaded = new AtomicLong(0);

		client.addObserver((o, arg) -> {
			Client.ClientState state = client.getState();
			float progress = client.getTorrent().getCompletion();
			double currentTime = getCurrentTime();
			double elapsedTime = (currentTime - this.startTime) / 1000.0;
			long downloadedBytes = client.getTorrent().getDownloaded();
			long deltaDownloaded = downloadedBytes - lastDownloaded.getAndSet(downloadedBytes);
			double instantSpeed = deltaDownloaded / 1.0;

			this.updateProgress(progress, state.toString(), instantSpeed, downloadedBytes / elapsedTime,
					client.getPeers().size());
		});

		client.download();

		while (!client.getState().equals(Client.ClientState.SEEDING)) {
			Thread.sleep(1000);
		}
		client.stop();
		this.updateStatus("\nTorrent download completed!");
	}

	private void downloadDirectLink(String fileUrl) throws IOException {
	    URL url = new URL(fileUrl);
	    HttpURLConnection connection = openConnection(url);

	    boolean acceptRanges = connection.getHeaderField("Accept-Ranges") != null;
	    long fileSize = connection.getContentLengthLong();
	    String fileName = getFileName(connection, fileUrl);

	    File outputFile = new File("downloads", fileName);
	    if (!outputFile.getParentFile().exists()) {
	        outputFile.getParentFile().mkdirs();
	    }

	    if (fileSize > 0) {
	        try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
	            raf.setLength(fileSize);
	        } catch (IOException e) {
	            updateStatus("Error setting file length: " + e.getMessage());
	            e.printStackTrace();
	        }
	    }
	    AtomicLong totalBytesDownloaded = new AtomicLong(0);
	    if (acceptRanges && fileSize > 0) {
	        long segmentSize = fileSize / NUM_SEGMENTS;
	        
	        // futures dùng get() để kiểm tra các luồng tải phân đoạn có hoàng thành hay không
	        // chú ý không thêm monitorObserver vào futures vì không kiểm tra hoàng thành
	        List<Future<?>> futures = new ArrayList<>();
	        
	        for (int i = 0; i < NUM_SEGMENTS; i++) {
	            long startByte = i * segmentSize;
	            long endByte = (i == NUM_SEGMENTS - 1) ? fileSize - 1 : (i + 1) * segmentSize - 1;
	            final int segmentNumber = i;
	            // Thêm các luồng tải phân đoạn vào executor
	            futures.add(executor.submit(() -> {
	                try {
	                    downloadSegment(fileUrl, startByte, endByte, outputFile, segmentNumber, totalBytesDownloaded, fileSize);
	                } catch (IOException e) {
	                    e.printStackTrace();
	                    updateStatus("Error in downloading segment: " + e.getMessage());
	                }
	            }));
	        }
	        
	        // Thêm luồng thông báo quá trình vào excutor
	        executor.submit(() -> monitorObserver(totalBytesDownloaded, fileSize));
	        // Hoàn tất xử lý các phân đoạn
	        completeDownload(futures, fileSize);
	    } else { // tải thông thường nếu không cho phép  tải phân đoạn 
	        performSingleThreadDownload(connection, outputFile, totalBytesDownloaded);
	    }
	}

	private void monitorObserver(AtomicLong totalBytesDownloaded, Long fileSize) {
	    while (!Thread.currentThread().isInterrupted())
	    {
	    	try {
		        // Xử lý pause
		        lock.lock();
		        try {
		            while (!this.runningFlag) {
		                try {
		                    pauseCondition.await();
		                } catch (InterruptedException e) {
		                    Thread.currentThread().interrupt();
		                    return;
		                }
		            }
		        } finally {
		            lock.unlock();
		        }
		        
		        // thông báo tổng quan
                updateOverallProgress(totalBytesDownloaded.get(), fileSize);
	            
		        // tạm nghỉ luồng monitorObserver
		        Thread.sleep(3000);
		    } catch (InterruptedException e) {
		        Thread.currentThread().interrupt();
		    }
	    }
		
	}

	private void completeDownload(List<Future<?>> futures, long fileSize) throws IOException {
	    try {
	    	// future.get() : đợi một luồng chạy xong
	    	 for (Future<?> future : futures) {
	    		 future.get();
	         }
	        // thông báo
	        updateOverallProgress(fileSize, fileSize);
	        updateStatus("Download completed successfully!");
	    } catch (InterruptedException | ExecutionException e) {
	        updateStatus("Download failed: " + e.getMessage());
	        throw new IOException("Download failed", e);
	    } finally {
	        this.runningFlag = false;
	        executor.shutdownNow();  // Hủy tất cả các luồng
	    }
	}
	
	private void downloadSegment(String fileUrl, long startByte, long endByte, File outputFile, int segmentNumber,
	        AtomicLong totalBytesDownloaded, long fileSize) throws IOException {
		// thiết lập kết nối http
		URL url = new URL(fileUrl);
	    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	    connection.setRequestMethod("GET");
	    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
	    connection.setRequestProperty("Range", "bytes=" + startByte + "-" + endByte);
	    // Mở stream đọc và ghi
	    RandomAccessFile raf = new RandomAccessFile(outputFile, "rw");
	    InputStream in = connection.getInputStream();
        raf.seek(startByte);
        byte[] buffer = new byte[8192];
        int bytesRead;
        long bytesDownloaded = 0;
        double lastUpdateTime = getCurrentTime();
        long lastBytesDownloaded = 0;
        double currentTime;
		try { 		
	        while ((bytesRead = in.read(buffer)) != -1) {
	            // Kiểm tra interrupt 
	            if (Thread.currentThread().isInterrupted()) {
	                return;
	            }
	            // Xử lý pause
	            lock.lock();
	            try {
	                while (!this.runningFlag) {
	                    try {
	                        pauseCondition.await();
	                    } catch (InterruptedException e) {
	                        Thread.currentThread().interrupt();
	                        connection.disconnect();
	                        return;
	                    }
	                }
	            } finally {
	                lock.unlock();
	            }
	            // Tải và cập nhật tiến trình
	            raf.write(buffer, 0, bytesRead);
	            bytesDownloaded += bytesRead;
	            totalBytesDownloaded.addAndGet(bytesRead);
	            // thông báo mỗi 2 giây
	            currentTime = getCurrentTime();
	            if (currentTime - lastUpdateTime >= 2000) {
	                // Tính toán tốc độ tải
	                double timeElapsed = currentTime - lastUpdateTime;
	                long bytesDelta = bytesDownloaded - lastBytesDownloaded;
	                double speedInBytesPerSecond = (bytesDelta * 1000.0) / timeElapsed;

	                // Tính toán tiến trình phân đoạn
	                double segmentProgress = (bytesDownloaded * 100.0) / (endByte - startByte + 1);
	                // Cập nhật thông báo
	                updateSegmentProgress(segmentNumber, bytesDownloaded, endByte - startByte + 1, segmentProgress, speedInBytesPerSecond);
	                // Cập nhật thời gian và bytes cho lần tính toán tiếp theo
	                lastUpdateTime = currentTime;
	                lastBytesDownloaded = bytesDownloaded;
	            }
	        }
	        updateSegmentProgress(segmentNumber, bytesDownloaded, endByte - startByte + 1, 100,0);
	    } catch (IOException e) {
	        updateStatus("Error in segment " + (segmentNumber+1) + ": " + e.getMessage());
	        throw e;
	    } finally {
	        try {
	            if (raf != null) {
	                try {
	                    raf.close();
	                } catch (IOException e) {}
	            }
	        } finally {
	            try {
	                if (in != null) {
	                    try {
	                        in.close();
	                    } catch (IOException e) {}
	                }
	            } finally {
	                if (connection != null) {
	                    try {
	                        connection.disconnect();
	                    } catch (Exception e) {}
	                }
	            }
	        }
	    }
	}

	private void performSingleThreadDownload(HttpURLConnection connection, File outputFile, AtomicLong totalBytesDownloaded) throws IOException {
	    updateStatus("Kich thuoc file khong xac dinh, he thong se thuc hien tai thong thuong!");
	    updateStatus("Vui long doi trong giay lat . . .");
	    try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(outputFile)) {
	        byte[] buffer = new byte[8192];
	        int bytesRead;
	        while ((bytesRead = in.read(buffer)) != -1) {
	            out.write(buffer, 0, bytesRead);
	            totalBytesDownloaded.addAndGet(bytesRead);
	        }
	    }
	    updateStatus("Download completed successfully!");
	}
	
	private HttpURLConnection openConnection(URL url) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		connection.setRequestProperty("User-Agent", "Mozilla/5.0");
		connection.setInstanceFollowRedirects(true);

		int redirectCount = 0;
		while (redirectCount < MAX_REDIRECTS) {
			int status = connection.getResponseCode();
			if (status != HttpURLConnection.HTTP_MOVED_TEMP && status != HttpURLConnection.HTTP_MOVED_PERM
					&& status != HttpURLConnection.HTTP_SEE_OTHER) {
				break;
			}
			String newUrl = connection.getHeaderField("Location");
			connection.disconnect();
			connection = (HttpURLConnection) new URL(newUrl).openConnection();
			connection.setRequestProperty("User-Agent", "Mozilla/5.0");

			redirectCount++;
		}

		return connection;
	}

	private static String getFileName(HttpURLConnection connection, String fileUrl) {
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
	
	private static String formatFileSize(long size) {
		String[] units = { "B", "KB", "MB", "GB", "TB" };
		int unitIndex = 0;
		double fileSize = size;

		while (fileSize > 1024 && unitIndex < units.length - 1) {
			fileSize /= 1024;
			unitIndex++;
		}

		return df.format(fileSize) + " " + units[unitIndex];
	}

	private void updateOverallProgress(long totalBytesDownloaded, long fileSize) {
		if (statusArea != null && progressBar != null) {
			double progress = (double) totalBytesDownloaded / fileSize * 100;
			double nowTime = getCurrentTime();
			double elapsedTime = nowTime - this.startTime - this.totalPauseTime;
			double speed = totalBytesDownloaded / elapsedTime;    // milisecond
			double estimatedTimeRemaining = (fileSize - totalBytesDownloaded) / speed;
			speed *=1000; // tốc độ trên 1 giây
			String progressText = String.format("Overall Progress: %s / %s (%.2f%%) - Speed: %s/s - Elapsed: %s - ETA: %s\n\n",
					formatFileSize(totalBytesDownloaded), formatFileSize(fileSize), progress,
					formatFileSize((long) speed), formatTime(elapsedTime), formatTime(estimatedTimeRemaining));
			Platform.runLater(() -> {
				progressBar.setProgress(progress / 100);
				statusArea.appendText(progressText);
			});
		}
	}

	private void updateSegmentProgress(int segmentNumber, long bytesDownloaded, 
			long segmentSize,double segmentProgress, double segmentSpeed) {
		if (statusArea != null) {
			double nowTime = getCurrentTime();
			String progressText = String.format("Segment %d: %s / %s (%.2f%%) - Speed: %s/s - Elapsed: %s\n",
					segmentNumber+1, formatFileSize(bytesDownloaded), formatFileSize(segmentSize), segmentProgress,
					formatFileSize((long) segmentSpeed), formatTime(nowTime-this.startTime-this.totalPauseTime));
			Platform.runLater(() -> {
				statusArea.appendText(progressText);
			});
		}
	}

	private void updateProgress(double progress, String state, double instantSpeed, double averageSpeed, int peers) {
		if (progressBar != null && statusArea != null) {
			Platform.runLater(() -> {
				progressBar.setProgress(progress / 100);
				statusArea.setText(String.format(
						"Progress: %.2f%% - State: %s - Current Speed: %s/s - Average Speed: %s/s - Peers: %d",
						progress, state, formatFileSize((long) instantSpeed), formatFileSize((long) averageSpeed),
						peers));
			});
		}
	}
	
	private double getCurrentTime() {
		return System.currentTimeMillis();
	}
	
	private static String formatTime(double milliseconds) {
	    int seconds = (int) (milliseconds / 1000); 
	    int hours = seconds / 3600;
	    seconds %= 3600; 
	    int minutes = seconds / 60;
	    seconds %= 60; 
	    StringBuilder timeString = new StringBuilder();
	    if (hours > 0) {
	        timeString.append(hours).append(" hours ");
	    }
	    if (minutes > 0) {
	        timeString.append(minutes).append(" minutes ");
	    }
	    timeString.append(String.format("%d seconds", seconds));
	    return timeString.toString().trim(); 
	}

	private void updateStatus(String message) {
		if (statusArea != null) {
			Platform.runLater(() -> {
				statusArea.appendText(message + "\n");
			});
		}
	}
}
