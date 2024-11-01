package downloader;

import javafx.scene.layout.*;

class DownloadInfo {
    private final AdvancedDownloader downloader;
    private final HBox urlRow;
    private String currentUrl;

    public DownloadInfo(AdvancedDownloader downloader, HBox urlRow) {
        this.downloader = downloader;
        this.urlRow = urlRow;
    }
    
    public void start() {
		downloader.startDownload(currentUrl);
	}
    
    public void pause() {
    	downloader.pauseDownload();
	}
    
    public void resume() {
    	downloader.resumeDownload();
	}
    
    
    public boolean getRunningFlag() {
    	return downloader.getRunningFlag();
	}
    
    public boolean getStartFlag() {
		return this.downloader.getStartFlag();
	}
    
    public void cancelDownload() {
		this.downloader.cancelDownload();
	}
    
    public HBox getUrlRow() {
        return urlRow;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public void setCurrentUrl(String url) {
        this.currentUrl = url;
    }
   
    
}