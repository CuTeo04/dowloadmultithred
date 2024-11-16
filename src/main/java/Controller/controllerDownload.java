package Controller;

import Model.downloadObject;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class controllerDownload extends downloadObject {
	private HBox urlRow;

	public controllerDownload(HBox urlRow) {
		super();
		this.urlRow = urlRow;
	}

	public HBox getUrlRow() {
		return urlRow;
	}

	public void setUrlRow(HBox urlRow) {
		this.urlRow = urlRow;
	}

	public boolean isSelectedChecbox() {
		CheckBox checkBox = (CheckBox) urlRow.getChildren().get(0);
		if (checkBox.isSelected())
			return true;
		else
			return false;
	}

	public void setInputCurrentUrlText() {
		TextField urlField = (TextField) getUrlRow().getChildren().get(1);
		this.urlInput = (urlField.getText());
	}
	
	public void setDetailText(String txt) {
		VBox statusContainer = (VBox) this.urlRow.getChildren().get(3);
		TextArea statusArea = (TextArea) statusContainer.getChildren().get(1);
		statusArea.appendText("\n" + txt );
		
	}
	
	public void updateProgressUI() {
		if (this.isStarted() && this.downloader.getRunningFlag())
			 {
				VBox statusContainer = (VBox) this.urlRow.getChildren().get(3);
				ProgressBar progressBar = (ProgressBar) statusContainer.getChildren().get(0);
				progressBar.setProgress(this.downloader.getProgress());
				setDetailText(this.downloader.getDetailText());
			} 
	}
}