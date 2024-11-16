package Model;

public class downloadObject {
	public abstractDownloadObject downloader;
	protected String urlInput;

	public downloadObject() {
	}

	public boolean downloaderNotNull() {
		if (downloader == null)
			return false;
		else
			return true;
	}

	public void start() {
		if (urlInput.endsWith(".torrent"))
			this.downloader = new downloadTorrent();
		else
			this.downloader = new downloadHttpDriectLink();
		this.downloader.start(urlInput);
	}
}
