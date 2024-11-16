package View;

import Controller.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.core.appender.rolling.action.IfFileName;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.awt.Desktop;
import java.io.IOException;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class AdvancedDownloaderGUI extends Application {

	private int urlCount = 0;
	private static final int MAX_URL_FIELDS = 7;
	private List<controllerDownload> downloads;

	@Override
	public void start(Stage primaryStage) {
		downloads = new ArrayList<>();
		primaryStage.setTitle("Advanced Downloader");
		VBox mainLayout = createMainLayout(primaryStage);
		mainLayout.getStyleClass().add("main-container");
		Scene scene = new Scene(mainLayout, 1200, 600);
		scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
		primaryStage.setScene(scene);
		primaryStage.show();

		
		//// note: cập nhật dữ liệu
		Timeline progressUpdateTimeline;
		progressUpdateTimeline = new Timeline(new KeyFrame(Duration.seconds(1.5), event -> {
			downloads.forEach(info -> {
				info.updateProgressUI();
			});
		}));
		// Đặt timeline chạy vô hạn
		progressUpdateTimeline.setCycleCount(Timeline.INDEFINITE);
		progressUpdateTimeline.play();
		
		//// note: hủy tải trước khi đóng ứng dụng
		primaryStage.setOnCloseRequest(event -> {
			if (progressUpdateTimeline != null) {
				progressUpdateTimeline.stop();
			}
			handleShutdown();
		});
	}

	private VBox createMainLayout(Stage primaryStage) {
		Label headerLabel = new Label("Advanced File Downloader");
		headerLabel.getStyleClass().add("header-label");
		Label urlLabel = new Label("Enter URLs or Paths to .torrent (one per line):");
		urlLabel.getStyleClass().add("section-label");

		VBox urlContainer = new VBox(10);
		urlContainer.getStyleClass().add("url-container");
		addUrlRow(urlContainer);
		ScrollPane scrollPane = new ScrollPane(urlContainer);
		scrollPane.setFitToWidth(true);
		scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scrollPane.getStyleClass().add("url-scroll-pane");
		VBox.setVgrow(scrollPane, Priority.ALWAYS);

		HBox buttonBox1 = createButtonBox1(primaryStage, urlContainer);
		HBox buttonBox2 = createButtonBox2(primaryStage, urlContainer);
		buttonBox1.getStyleClass().add("button-box");
		buttonBox2.getStyleClass().add("button-box");

		VBox mainLayout = new VBox(20);
		mainLayout.getChildren().addAll(headerLabel, urlLabel, scrollPane, buttonBox1, buttonBox2);
		mainLayout.setAlignment(Pos.TOP_CENTER);
		mainLayout.setPadding(new Insets(20));

		return mainLayout;
	}

	private HBox createButtonBox1(Stage primaryStage, VBox urlContainer) {
		Button downloadButton = createStyledButton("Download", "download");
		downloadButton.setOnAction(e -> downloadSelected(urlContainer));
		Button pauseButton = createStyledButton("Pause", "pause");
		pauseButton.setOnAction(e -> pauseSelected(urlContainer));
		Button resumeButton = createStyledButton("Resume", "pause");
		resumeButton.setOnAction(e -> resumeSelected(urlContainer));
		HBox buttonBox = new HBox(15);
		buttonBox.getChildren().addAll(downloadButton, pauseButton, resumeButton);
		buttonBox.setAlignment(Pos.CENTER);
		return buttonBox;
	}

	private HBox createButtonBox2(Stage primaryStage, VBox urlContainer) {
		Button addUrlButton = createStyledButton("Add URL", "add-url");
		addUrlButton.setOnAction(e -> addUrlRow(urlContainer));
		Button browseButton = createStyledButton("Browse Torrent", "browse");
		browseButton.setOnAction(e -> openFileChooser(primaryStage, urlContainer));
		Button browseFolderButton = createStyledButton("Open Downloads folder", "browse");
		browseFolderButton.setOnAction(e -> openDownloadsFolder());
		HBox buttonBox = new HBox(15);
		buttonBox.getChildren().addAll(addUrlButton, browseButton, browseFolderButton);
		buttonBox.setAlignment(Pos.CENTER);
		return buttonBox;
	}

	private Button createStyledButton(String text, String styleClass) {
		Button button = new Button(text);
		button.getStyleClass().add("custom-button");
		button.getStyleClass().add(styleClass + "-button");
		return button;
	}

	private HBox createUrlRow(int index) {
		CheckBox checkBox = new CheckBox();
		checkBox.getStyleClass().add("custom-checkbox");
		TextField urlField = new TextField();
		urlField.setPromptText("Enter URL or file path here...");
		urlField.getStyleClass().add("url-field");
		urlField.setPrefWidth(400);
		Button deleteButton = new Button("Delete");
		deleteButton.getStyleClass().addAll("custom-button", "delete-button");
		deleteButton.setOnAction(e -> deleteUrlRow((HBox) deleteButton.getParent()));
		VBox statusContainer = createStatusContainer();
		statusContainer.getStyleClass().add("status-container");
		HBox.setHgrow(statusContainer, Priority.ALWAYS);
		HBox urlRow = new HBox(10);
		urlRow.getChildren().addAll(checkBox, urlField, deleteButton, statusContainer);
		urlRow.getStyleClass().add("url-row");
		urlRow.setAlignment(Pos.CENTER_LEFT);
		controllerDownload downloader = new controllerDownload(urlRow);
		downloads.add(downloader);
		return urlRow;
	}

	private VBox createStatusContainer() {
		ProgressBar progressBar = new ProgressBar(0);
		progressBar.getStyleClass().add("custom-progress-bar");
		progressBar.setMaxWidth(Double.MAX_VALUE);
		TextArea statusArea = new TextArea();
		statusArea.setEditable(false);
		statusArea.setWrapText(true);
		statusArea.setPrefHeight(80);
		statusArea.setMaxWidth(Double.MAX_VALUE);
		statusArea.getStyleClass().add("status-area");
		VBox statusContainer = new VBox(5);
		statusContainer.getChildren().addAll(progressBar, statusArea);
		VBox.setVgrow(statusArea, Priority.ALWAYS);
		return statusContainer;
	}

	private void openFileChooser(Stage stage, VBox urlContainer) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select Torrent File");
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Torrent Files", "*.torrent"));
		File selectedFile = fileChooser.showOpenDialog(stage);

		if (selectedFile != null) {
			addUrlRow(urlContainer);
			HBox lastUrlRow = (HBox) urlContainer.getChildren().get(urlCount - 1);
			TextField lastUrlField = (TextField) lastUrlRow.getChildren().get(1);
			lastUrlField.setText(selectedFile.getAbsolutePath());
		}
	}

	private void openDownloadsFolder() {
		try {
			File currentDirectory = new File(System.getProperty("user.dir"));
			File downloadsFolder = new File(currentDirectory, "Downloads");

			if (downloadsFolder.exists() && downloadsFolder.isDirectory()) {
				Desktop.getDesktop().open(downloadsFolder);
			} else {
				showAlert("Error", "Downloads folder not found in the current directory.");
			}
		} catch (IOException e) {
			e.printStackTrace();
			showAlert("Error", "Unable to open Downloads folder.");
		}
	}

	private void showAlert(String title, String content) {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle(title);
		alert.setHeaderText(null);
		alert.setContentText(content);
		alert.getDialogPane().getStyleClass().add("custom-alert");
		alert.showAndWait();
	}

	private void addUrlRow(VBox urlContainer) {
		if (urlCount < MAX_URL_FIELDS) {
			HBox urlRow = createUrlRow(urlCount);
			urlContainer.getChildren().add(urlRow);
			urlCount++;
		} else {
			showAlert("Limit Reached", "You can only add a maximum of " + MAX_URL_FIELDS + " URLs.");
		}
	}

	////////////// note
	private void deleteUrlRow(HBox urlRow) { // hoặc xóa bằng checkbox
		downloads.removeIf(info -> {
			if (info.getUrlRow() == urlRow) {
				info.downloader.cancel();
				return true;
			}
			return false;
		});
		VBox parent = (VBox) urlRow.getParent();
		parent.getChildren().remove(urlRow);
		urlCount--;
	}

	//////////////// note
	private void downloadSelected(VBox urlContainer) {
		downloads.forEach(info -> {
			HBox urlRow = info.getUrlRow();
			if (!info.downloaderNotNull() && info.isSelectedChecbox()) {
				new Thread(() -> {
					try {
						info.setInputCurrentUrlText();
						info.start();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}).start();
			}
		});
	}

	//////////////// note

	private void pauseSelected(VBox urlContainer) {
		downloads.forEach(info -> {
			if (info.downloaderNotNull() && info.isSelectedChecbox() && info.downloader.getRunningFlag()) {
				info.setDetailText("Pause. . .");
				info.downloader.pause();
			}
		});
	}

	//////////////// note
	private void resumeSelected(VBox urlContainer) {
		downloads.forEach(info -> {
			if (info.downloaderNotNull() && info.isSelectedChecbox() && !info.downloader.getRunningFlag()) {
				info.setDetailText("Resume. . . ");
				info.downloader.resume();
			}
		});
	}

	//////// note: hủy các file đang tải để tránh lỗi luồng
	private void handleShutdown() {
		downloads.forEach(info -> {
			if (info.downloaderNotNull())
				info.downloader.cancel();
			System.exit(0);
		});
	}

	public static void main(String[] args) {
		launch(args);
	}
}