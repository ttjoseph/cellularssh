package com.zwerdog.cellularssh;

import net.rim.blackberry.api.menuitem.*;
import net.rim.device.api.ui.*;
import net.rim.device.api.ui.container.*;
import net.rim.device.api.ui.component.*;
import net.rim.device.api.system.CoverageInfo;
import net.rim.device.api.system.Bitmap;

public class CellularSSHApp extends UiApplication
{
        BasicEditField serverField_, portField_, usernameField_;
        PasswordEditField passwordField_;
        CheckboxField doGnuScreenMagic_;

        public CellularSSHApp()
        {
                // Add UI elements
                MainScreen screen = new MainScreen()
                {
                        // Suppress "data not saved" dialog
                        public boolean onSavePrompt()
                        { return true; }
                };
                screen.setDirty(false);
                serverField_ = new BasicEditField("Server: ", "", 256, EditField.FILTER_URL);
                portField_ = new BasicEditField("Port: ", "22", 5, EditField.FILTER_INTEGER);
                usernameField_ = new BasicEditField("Username: ", "");
                passwordField_ = new PasswordEditField("Password: ", "");
                
                screen.setTitle(new LabelField("CellularSSH"));
                screen.add(serverField_);
                screen.add(portField_);
                screen.add(usernameField_);
                screen.add(passwordField_);
                screen.add(new SeparatorField());
                screen.add(new RichTextField("Once connected, use the Sym key to type a tab, or click the trackball to easily enter multiple characters at once.",
                                Field.NON_FOCUSABLE));
                
                TerminalEmulatorField te = new TerminalEmulatorField(50, 300);
                te.write("$ ls -a\r\n" + ((char) 0x1b) + "[34mDELETED!\r\nThis is just a test version...\r\n"
                        + ((char) 0x1b) + "[0m$ logout\n");
                screen.add(te);
                
                // Connect menu item
                screen.addMenuItem(new MenuItem("Connect", 0, 100)
                {
                        public void run()
                        {
                                // Validate the user's entries before trying to make a connection
                                if(serverField_.getText().length() <= 0)
                                        Dialog.alert("Please specify a server to connect to.");
                                else if(portField_.getText().length() <= 0)
                                        Dialog.alert("Please specify a port to connect to. The SSH default is 22.");
                                else if(usernameField_.getText().length() <= 0)
                                        Dialog.alert("Please enter a username.");
                                // The COVERAGE_CARRIER constant has been deprecated since OS 4.2.1
                                // else if(!CoverageInfo.isCoverageSufficient(CoverageInfo.COVERAGE_CARRIER))
                                //        Dialog.alert("Network access doesn't seem to be working.");
                                else
                                {
                                        Session session = new Session(serverField_.getText(), 
                                                        Integer.parseInt(portField_.getText()), 
                                                        usernameField_.getText(), 
                                                        passwordField_.getText());
                                        pushScreen(new SessionScreen(session));
                                }
                        }
                });
                
                screen.addMenuItem(new MenuItem("About", 2, 101)
                {
                        public void run()
                        {
                                pushScreen(new Dialog(Dialog.D_OK, 
                                                "CellularSSH Client, by and (c) T. Joseph except for portions" +
                                                " copyrighted by others. Includes part of the BouncyCastle cryptography" + 
                                                " library and the ProggyTiny font from Upper Bounds Interactive. Disclaimer:" +
                                                " don't use this software for things like nuclear reactors and medical devices." +
                                                " Also, no guarantees of fitness or merchantability are made.",
                                                0, Bitmap.getBitmapResource("icon.png"), 0));
                                
                        }
                });
                
                pushScreen(screen);
                
                serverField_.setFocus();
        }
        
        /**
         * The main method: where the magic begins.
         * 
         * @param args
         */
        public static void main(String[] args)
        {
                CellularSSHApp app = new CellularSSHApp();
                app.enterEventDispatcher();
        }
        
}
