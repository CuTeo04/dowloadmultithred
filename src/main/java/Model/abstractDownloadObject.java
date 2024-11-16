package Model;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public abstract class abstractDownloadObject {
	protected volatile boolean runningFlag;
	protected volatile double totalPauseTime;
	protected volatile double lastPauseTime;
	protected volatile double startTime;
	protected ExecutorService executor;
	protected  ReentrantLock lock;
	protected  Condition pauseCondition;
	
	protected String input;
	protected double progress;
	protected String detailText;

	public abstract void start(String urlInput);

	public abstract void pause();

	public abstract void resume();

	public abstract void cancel();

	public abstract boolean getRunningFlag();

	public void setInputText(String input) {
		this.input = input;
	}

	public double getProgress() {
		return this.progress;
	}

	public String getDetailText() {
		return this.detailText;
	}

}
