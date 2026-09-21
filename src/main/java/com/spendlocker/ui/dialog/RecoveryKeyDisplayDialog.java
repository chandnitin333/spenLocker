package com.spendlocker.ui.dialog;

import com.spendlocker.util.DialogUtil;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Shows a newly generated recovery key exactly once, with a one-click copy button. */
public class RecoveryKeyDisplayDialog {

    public static void show(String recoveryKey) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Save Your Recovery Key");
        dialog.setHeaderText("This is shown only once. If you ever forget your master password,\nthis key is the only way back into your vault.");
        DialogUtil.center(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextField keyField = new TextField(recoveryKey);
        keyField.setEditable(false);
        keyField.setStyle("-fx-font-family: monospace; -fx-font-size: 16px;");
        keyField.setPrefWidth(300);

        Button copyButton = new Button("Copy", new FontIcon(Feather.COPY));
        copyButton.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(recoveryKey);
            Clipboard.getSystemClipboard().setContent(content);
            copyButton.setText("Copied!");
        });

        HBox row = new HBox(8, keyField, copyButton);
        Label warning = new Label("Store it somewhere safe (a password manager, printed note, etc.) — not in this app.");
        warning.getStyleClass().add("text-caption");
        warning.setWrapText(true);

        VBox content = new VBox(12, row, warning);
        content.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinWidth(460);

        dialog.showAndWait();
    }
}
