package cn.edu.sustech.cs209.chatting.client;

import cn.edu.sustech.cs209.chatting.common.Message;
import cn.edu.sustech.cs209.chatting.common.MsgType;
import cn.edu.sustech.cs209.chatting.common.OOS_OIS;
import cn.edu.sustech.cs209.chatting.common.Users;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;


import java.io.IOException;

import java.net.Socket;
import java.net.URL;
import java.sql.Time;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;


public class Controller implements Initializable {

    private OOS_OIS.MyObjectOutputStream moos;

    public Set<String> userSet = new HashSet<>();
    ObservableList<String> stringObservableList;
    ObservableList<Message> mesObservableList = FXCollections.observableArrayList();

    @FXML
    private TextArea inputArea;

    @FXML
    ListView<Message> chatContentList;

    String username;

    @FXML
    private Label currentUsername;
    @FXML
    private Label talkWith;
    private String talkTo = null;

    @FXML
    public ListView<String> chatList;

    @FXML
    public Label currentOnlineCnt;


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        Dialog<String> dialog = new TextInputDialog();
        dialog.setTitle("Login");
        dialog.setHeaderText(null);
        dialog.setContentText("Username:");


        Optional<String> input = dialog.showAndWait();
        if (input.isPresent() && !input.get().isEmpty()) {
            /*
               TODO: Check if there is a user with the same name among the currently logged-in users,
                     if so, ask the user to change the username
             */
            System.out.println(Users.user_socket_map);
            if (Users.user_socket_map.containsKey(input.get())) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("repetitive username");
                alert.setHeaderText("repetitive username");
                alert.setContentText("You entered a repetitive username, please change it later.");
                alert.showAndWait();
                Platform.exit();
            } else {
                username = input.get();
                setCurrentUsername(username);
                try {
                    //创建一个client，以及其中的读写线程
                    Client client = new Client(username, this);

                    //发送给相应的Server一个Message，说明自己连接上了，同时将自己放到users中
                    moos = client.getOs();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            System.out.println("Invalid username " + input + ", exiting");
            Platform.exit();
        }
        String displayTalkTo = "talking to: " + talkTo;
        talkWith.setText(displayTalkTo);

        chatList.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                talkTo = chatList.getSelectionModel().getSelectedItem();
                privateChatHelper();
                //将信息传递进user-user-msg中

            }
        });

        chatContentList.setCellFactory(new MessageCellFactory());
        chatContentList.setItems(mesObservableList);
    }

    @FXML
    public void createPrivateChat() {
        AtomicReference<String> user = new AtomicReference<>();

        //该stage为弹出供我们选择私聊对象的stage
        Stage stage = new Stage();
        ComboBox<String> userSel = new ComboBox<>();

        // FIXME: get the user list from server, the current user's name should be filtered out
        //将userset写入usersel
        for (String s : userSet) {
            if (!s.equals(username)) userSel.getItems().add(s);
        }

        Button okBtn = new Button("OK");
        okBtn.setOnAction(e -> {
            user.set(userSel.getSelectionModel().getSelectedItem());
            //将选中的聊天对象设置为 talkto
            talkTo = userSel.getSelectionModel().getSelectedItem();
            privateChatHelper();
            stage.close();
        });

        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20, 20, 20, 20));
        box.getChildren().addAll(userSel, okBtn);
        stage.setScene(new Scene(box));
        stage.showAndWait();

//        // TODO: if the current user already chatted with the selected user, just open the chat with that user
//        if (user.get().equals(talkTo)) {
//
//        }
//        // TODO: otherwise, create a new chat item in the left panel, the title should be the selected user's name
//        else {
//        }
    }

    /**
     * A new dialog should contain a multi-select list, showing all user's name.
     * You can select several users that will be joined in the group chat, including yourself.
     * <p>
     * The naming rule for group chats is similar to WeChat:
     * If there are > 3 users: display the first three usernames, sorted in lexicographic order, then use ellipsis with the number of users, for example:
     * UserA, UserB, UserC... (10)
     * If there are <= 3 users: do not display the ellipsis, for example:
     * UserA, UserB (2)
     */
    @FXML
    public void createGroupChat() {
        Stage GroupChatChooserStage = new Stage();
        List<CheckBox>chosenUser = new ArrayList<>();
        Label label = new Label("choose some friend and begin your group chat! ");
        VBox under_vbox = new VBox();
        VBox upper_vbox = new VBox();
        HBox hBox = new HBox();
        Label label1 = new Label("set a name for your group: ");
        TextField textField = new TextField();
        hBox.getChildren().addAll(label1,textField);
        //可选择的用户不包含当前用户，但是在创建完成群后要将当前用户加进去
        for (String s : userSet) {
            if (!s.equals(username)) {
                chosenUser.add(new CheckBox(s));
            }
        }
        Button okBtn = new Button("OK");
        okBtn.setOnAction(e->{
            //发送信息给server，让server知道客户端创建群聊

            GroupChatChooserStage.close();
        });
        upper_vbox.getChildren().addAll(chosenUser);
        under_vbox.getChildren().addAll(hBox,label,upper_vbox,okBtn);
        under_vbox.setAlignment(Pos.CENTER);
        under_vbox.setPadding(new Insets(20, 20, 20, 20));
        GroupChatChooserStage.setScene(new Scene(under_vbox));
        GroupChatChooserStage.showAndWait();
    }

    /**
     * Sends the message to the <b>currently selected</b> chat.
     * <p>
     * Blank messages are not allowed.
     * After sending the message, you should clear the text input field.
     */
    @FXML
    public void doSendMessage() throws IOException {
        // TODO
        //发送一个信息给server
        String inputFromKeyBoard = inputArea.getText();
        //清空原本的内容
        inputArea.setText("");
        //将message传给server
        Message message = new Message(System.currentTimeMillis(), username, talkTo, inputFromKeyBoard, MsgType.TALK);
        moos.writeObject(message);
        //加入自己的message显示中
        Platform.runLater(() -> {
            mesObservableList.add(message);
            System.out.println(mesObservableList);
            chatContentList.setItems(mesObservableList);
        });

    }

    /**
     * You may change the cell factory if you changed the design of {@code Message} model.
     * Hint: you may also define a cell factory for the chats displayed in the left panel, or simply override the toString method.
     */
    private class MessageCellFactory implements Callback<ListView<Message>, ListCell<Message>> {
        @Override
        public ListCell<Message> call(ListView<Message> param) {
            return new ListCell<Message>() {

                @Override
                public void updateItem(Message msg, boolean empty) {
                    super.updateItem(msg, empty);
                    if (empty || Objects.isNull(msg)) {
                        //阻止切换聊天对象时出现bug
                        setText(null);
                        setGraphic(null);
                        return;
                    }

                    HBox wrapper = new HBox();
                    Label nameLabel = new Label(msg.getSentBy());
                    Label msgLabel = new Label(msg.getData());

                    nameLabel.setPrefSize(50, 20);
                    nameLabel.setWrapText(true);
                    nameLabel.setStyle("-fx-border-color: black; -fx-border-width: 1px;");

                    if (username.equals(msg.getSentBy())) {
                        wrapper.setAlignment(Pos.TOP_RIGHT);
                        wrapper.getChildren().addAll(msgLabel, nameLabel);
                        msgLabel.setPadding(new Insets(0, 20, 0, 0));
                    } else {
                        wrapper.setAlignment(Pos.TOP_LEFT);
                        wrapper.getChildren().addAll(nameLabel, msgLabel);
                        msgLabel.setPadding(new Insets(0, 0, 0, 20));
                    }

                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setGraphic(wrapper);
                }
            };
        }
    }

    //左下角当前用户显示
    public void setCurrentUsername(String name) {
        currentUsername.setText("Current User: " + name);
    }

    //在线人数显示
    public void setCuNum(String a) {
        Platform.runLater(() -> currentOnlineCnt.setText("Online:" + a));
    }

    //设置好左侧聊天对象栏
    public void setLeftLV(String[] string) {
        Platform.runLater(() -> {
            ArrayList<String> str = new ArrayList<>();
            for (String s : string) {
                if (!s.equals(username)) {
                    str.add(s);
                }
            }
            String[] sss = new String[str.size()];
            for (int i = 0; i < str.size(); i++) {
                sss[i] = str.get(i);
            }
            stringObservableList = FXCollections.observableArrayList(Arrays.asList(sss));
            chatList.setItems(stringObservableList);
        });
    }

    //用于更新聊天内容
    public void setMsgLV(Message message) {
        Platform.runLater(() -> {
            mesObservableList.add(message);
            System.out.println(mesObservableList);
            chatContentList.setItems(mesObservableList);
        });
    }

    //用于在切换聊天对象时重新刷新聊天
    public void reWriteMsgLV() {
        Platform.runLater(() -> {
            mesObservableList = FXCollections.observableArrayList();
            chatContentList.setItems(mesObservableList);
        });
    }

    public void privateChatHelper() {
        //发送给server信息，告诉server该客户端talkTo的对象
        try {
            moos.writeObject(new Message(System.currentTimeMillis(), username, talkTo, "talkingTo", MsgType.TALKINGTO));
            System.out.println("talking to success");
            reWriteMsgLV();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        talkWith.setText("talking to: " + talkTo);
    }

}
