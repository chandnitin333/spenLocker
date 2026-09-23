package com.spendlocker.ui.dialog;

import com.spendlocker.util.DialogUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.net.URI;

/**
 * Shown once, before a brand-new vault is created — the user must read and accept before
 * registration can continue. Returning users who already accepted never see this again
 * (there's nothing to gate: the vault already exists).
 */
public class PrivacyPolicyDialog {

    private static final String SUMMARY = """
            Wealth Book runs entirely on your own computer. Here's what that means:

            •  Everything you enter — deposits, investments, expenses, documents — is stored in a single AES-256 encrypted vault file on this device. There is no server, so nobody but you can see, access, or transmit your data.

            •  Google Drive/Gmail sync is optional and only ever runs when you turn it on in Settings. It connects directly from your device to Google using your own account — no data passes through any third-party server, because none exists.

            •  Your data is never sold, shared, or used for advertising.

            •  Your master password unlocks the encryption. Nobody — including the developer — can recover it for you; that's why a recovery key is generated when you create your vault.""";

    public static boolean promptForAcceptance() {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.styleWith(dialog.getDialogPane());
        dialog.setTitle("Wealth Book");

        ButtonType acceptType = new ButtonType("Accept & Continue", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(acceptType, ButtonType.CANCEL);

        FontIcon icon = new FontIcon(Feather.SHIELD);
        icon.setIconSize(32);
        icon.getStyleClass().add("brand-icon");

        Label title = new Label("Before you start");
        title.getStyleClass().add("title-2");

        VBox header = new VBox(8, icon, title);
        header.setAlignment(Pos.CENTER);

        Label summary = new Label(SUMMARY);
        summary.setWrapText(true);
        summary.setMaxWidth(440);

        Hyperlink fullPolicyLink = new Hyperlink("Read the full privacy policy");
        fullPolicyLink.setOnAction(e -> openFullPolicy());

        CheckBox acceptBox = new CheckBox("I have read and accept the Privacy Policy");

        VBox content = new VBox(14, header, summary, fullPolicyLink, new Separator(), acceptBox);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(24, 28, 12, 28));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinWidth(500);

        Node acceptButton = dialog.getDialogPane().lookupButton(acceptType);
        acceptButton.setDisable(true);
        acceptBox.selectedProperty().addListener((obs, old, checked) -> acceptButton.setDisable(!checked));

        return dialog.showAndWait().filter(b -> b == acceptType).isPresent();
    }

    private static void openFullPolicy() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("https://chandnitin333.github.io/spenLocker/docs/privacy-policy.html"));
            }
        } catch (Exception ignored) {
            // Opening the browser is a courtesy; the summary above is already the substance.
        }
    }
}
