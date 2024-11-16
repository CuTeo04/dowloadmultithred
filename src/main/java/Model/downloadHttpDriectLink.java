package Model;

import util.*;

import java.io.*;
import java.net.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class downloadHttpDriectLink extends abstractDownloadObject {
	private static final int NUM_SEGMENTS = 4;
	public downloadHttpDriectLink() {
		this.progress = 0;
		this.runningFlag = false;
		this.startTime = 0;
		this.executor = Executors.newFixedThreadPool(NUM_SEGMENTS + 1);
		this.lock = new ReentrantLock();
		this.pauseCondition = lock.newCondition();
	}

	public void start(String urlIput) {
		this.detailText = "Đang chuẩn bị tải!";
		this.input = urlIput;
		this.startTime = time.getCurrentTime();
		try {
			URL url = new URL(input);
			String protocol = url.getProtocol().toLowerCase();
			switch (protocol) {
			case "http":
			case "https":
				this.runningFlag = true;
				downloadDirectLink(input);
				break;
			default:
				this.detailText = "Unsupported protocol: " + protocol;
			}
		} catch (Exception e) {
			this.detailText = "Error occurred: " + e.getMessage();
			e.printStackTrace();
		} finally {
			executor = null;
		}
	}

	public void cancel() {
		try {
			executor.shutdownNow();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void pause() {
		this.lastPauseTime = time.getCurrentTime();
		this.runningFlag = false;
	}

	public void resume() {
		this.totalPauseTime += time.getCurrentTime() - this.lastPauseTime;
		this.runningFlag = true;
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

	private void downloadDirectLink(String fileUrl) throws IOException {
		URL url = new URL(fileUrl);
		HttpURLConnection connection = httpConection.openConnection(url);
		boolean acceptRanges = connection.getHeaderField("Accept-Ranges") != null;
		long fileSize = connection.getContentLengthLong();
		String fileName = file.getFileName(connection, fileUrl);

		File outputFile = new File("downloads", fileName);
		if (!outputFile.getParentFile().exists()) {
			outputFile.getParentFile().mkdirs();
			synchronized (outputFile) {

			}
		}
		try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
			raf.setLength(fileSize);
		} catch (IOException e) {
			this.detailText = "Error setting file length: " + e.getMessage();
			e.printStackTrace();
		}
		AtomicLong totalBytesDownloaded = new AtomicLong(0);
		if (acceptRanges && fileSize > 0) {
			long segmentSize = (long) Math.ceil((double) fileSize / NUM_SEGMENTS);

			List<Future<?>> futures = new ArrayList<>();
			for (int i = 0; i < NUM_SEGMENTS; i++) {
				long startByte = i * segmentSize;
				long endByte = (i == NUM_SEGMENTS - 1) ? fileSize - 1 : (i + 1) * segmentSize - 1;
				final int segmentNumber = i;

				// Thêm các luồng tải phân đoạn vào executor
				futures.add(executor.submit(() -> {
					try {
						downloadSegment(fileUrl, startByte, endByte, outputFile, segmentNumber, totalBytesDownloaded);
					} catch (IOException e) {
						e.printStackTrace();
						this.detailText = "Error in downloading segment: " + e.getMessage();
					}
				}));
			}

			// Thêm luồng thông báo quá trình vào excutor
			executor.submit(() -> monitorObserver(totalBytesDownloaded, fileSize));
			// Hoàn tất xử lý các phân đoạn
			completeDownload(futures, fileSize);
		} else { // tải thông thường nếu không cho phép tải phân đoạn
			performSingleThreadDownload(connection, outputFile, totalBytesDownloaded);
		}
	}

	private void downloadSegment(String fileUrl, long startByte, long endByte, File outputFile, int segmentNumber,
			AtomicLong totalBytesDownloaded) throws IOException {
		// thiết lập kết nối http
		URL url = new URL(fileUrl);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		connection.setRequestProperty("User-Agent", "Mozilla/5.0");
		connection.setRequestProperty("Connection", "keep-alive");
		connection.setRequestProperty("Range", "bytes=" + startByte + "-" + endByte);

		// Mở stream đọc và ghi
		RandomAccessFile raf = new RandomAccessFile(outputFile, "rw");
		InputStream in = new BufferedInputStream(connection.getInputStream(), 100 * 1024 * 1024);
		raf.seek(startByte);
		byte[] buffer = new byte[100 * 1024 * 1024];
		int bytesRead;
		long bytesDownloaded = 0;
		double lastUpdateTime = time.getCurrentTime();
		long lastBytesDownloaded = 0;
		double currentTime;
		long elapsedTime = 0;
		long start = System.nanoTime();
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
				bytesDownloaded += bytesRead;
				totalBytesDownloaded.addAndGet(bytesRead);
				// thông báo mỗi 2 giây
				currentTime = time.getCurrentTime();
				if (currentTime - lastUpdateTime >= 1500) {
					// Tính toán tốc độ tải
					double timeElapsed = currentTime - lastUpdateTime;
					long bytesDelta = bytesDownloaded - lastBytesDownloaded;
					double speedInBytesPerSecond = (bytesDelta * 1000.0) / timeElapsed;

					// Tính toán tiến trình phân đoạn
					double segmentProgress = (bytesDownloaded * 100.0) / (endByte - startByte + 1);
					// Cập nhật thông báo
					updateSegmentProgress(segmentNumber, bytesDownloaded, endByte - startByte + 1, segmentProgress,
							speedInBytesPerSecond);
					// Cập nhật thời gian và bytes cho lần tính toán tiếp theo
					lastUpdateTime = currentTime;
					lastBytesDownloaded = bytesDownloaded;
				}
			}
			updateSegmentProgress(segmentNumber, bytesDownloaded, endByte - startByte + 1, 100, 0);
			return;
		} catch (IOException e) {
			this.detailText = "Error in segment " + (segmentNumber + 1) + ": " + e.getMessage();
			throw e;
		} finally {
			try {
				if (raf != null) {
					try {
						raf.close();
					} catch (IOException e) {
					}
				}
			} finally {
				try {
					if (in != null) {
						try {
							in.close();
						} catch (IOException e) {
						}
					}
				} finally {
					if (connection != null) {
						try {
							buffer = null;
							connection.disconnect();
						} catch (Exception e) {
						}
					}
				}
			}
		}
	}

	private void monitorObserver(AtomicLong totalBytesDownloaded, Long fileSize) {
		while (!Thread.currentThread().isInterrupted()) {
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
				updateOverallProgress(totalBytesDownloaded.get(), fileSize);
				Thread.sleep(1700);
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
			updateOverallProgress(fileSize, fileSize);
			this.detailText = "Download completed successfully!";
		} catch (InterruptedException | ExecutionException e) {
			this.detailText = "Download failed: " + e.getMessage();
			throw new IOException("Download failed", e);
		} finally {
			this.runningFlag = false;
			executor.shutdownNow(); 
			executor = null;
		}
	}

	public void updateOverallProgress(long totalBytesDownloaded, long fileSize) {
		{
			double progress = (double) totalBytesDownloaded / fileSize * 100;
			double nowTime = time.getCurrentTime();
			double elapsedTime = nowTime - this.startTime - this.totalPauseTime;
			double speed = totalBytesDownloaded / elapsedTime; // milisecond
			double estimatedTimeRemaining = (fileSize - totalBytesDownloaded) / speed;
			speed *= 1000; // tốc độ trên 1 giây
			String detailText = String.format(
					"Overall Progress: %s / %s (%.2f%%) - Speed: %s/s - Elapsed: %s - ETA: %s \n",
					file.formatFileSize(totalBytesDownloaded), file.formatFileSize(fileSize), progress,
					file.formatFileSize((long) speed), time.formatTime(elapsedTime),
					time.formatTime(estimatedTimeRemaining));
			this.detailText = detailText;
			this.progress = progress / 100;
		}
	}

	public void updateSegmentProgress(int segmentNumber, long bytesDownloaded, long segmentSize, double segmentProgress,
			double segmentSpeed) {
		{
			double nowTime = time.getCurrentTime();
			String detailText = String.format("Segment %d: %s / %s (%.2f%%) - Speed: %s/s - Elapsed: %s\n",
					segmentNumber + 1, file.formatFileSize(bytesDownloaded), file.formatFileSize(segmentSize),
					segmentProgress, file.formatFileSize((long) segmentSpeed),
					time.formatTime(nowTime - this.startTime - this.totalPauseTime));
			this.detailText += detailText;
		}
	}

	private void performSingleThreadDownload(HttpURLConnection connection, File outputFile,
			AtomicLong totalBytesDownloaded) throws IOException {
		this.detailText = "Kích thước file không xác định, hệ thống sẽ thực hiện tải thông thường!";
		this.detailText = ("Vui lòng đợi giây lát . . .");
		try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(outputFile)) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = in.read(buffer)) != -1) {
				out.write(buffer, 0, bytesRead);
				totalBytesDownloaded.addAndGet(bytesRead);
			}
		}
		this.detailText = "Download completed successfully!";
	}
}
