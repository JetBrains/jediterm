package com.jediterm.app;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.util.ui.UIUtil;
import com.jediterm.terminal.ui.AbstractTabbedTerminalWidget;
import com.jediterm.terminal.ui.AbstractTabs;
import com.jediterm.terminal.ui.TerminalAction;
import com.jediterm.terminal.ui.TerminalWidget;
import com.jediterm.terminal.ui.settings.TabbedSettingsProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;

/**
 * @author traff
 */
public class JediTabbedTerminalWidget extends AbstractTabbedTerminalWidget<JediTerminalWidget> implements Disposable {

  private final TabbedSettingsProvider mySettingsProvider;
  private Disposable myParent;

  public JediTabbedTerminalWidget(@NotNull TabbedSettingsProvider settingsProvider,
                                  final @NotNull Function<Pair<TerminalWidget, String>, JediTerminalWidget> createNewSessionAction, @NotNull Disposable parent) {
    super(settingsProvider, input -> createNewSessionAction.apply(Pair.create(input, null)));

    mySettingsProvider = settingsProvider;
    myParent = parent;


    Disposer.register(parent, this);
  }


  @Override
  public JediTerminalWidget createInnerTerminalWidget() {
    return new JediTerminalWidget(mySettingsProvider, myParent);
  }

  @Override
  protected JediTerminalTabs createTabbedPane() {
    return new JediTerminalTabs(myParent);
  }

  public class JediTerminalTabs implements AbstractTabs<JediTerminalWidget> {
    private final JBEditorTabs myTabs;

    private final CopyOnWriteArraySet<TabChangeListener> myListeners = new CopyOnWriteArraySet<>();

    public JediTerminalTabs(@NotNull Disposable parent) {
      myTabs = new JBEditorTabs(parent) {
        @Override
        protected TabLabel createTabLabel(TabInfo info) {
          return new TerminalTabLabel(this, info);
        }
      };

      myTabs.addListener(new TabsListener.Adapter() {
        @Override
        public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
          for (TabChangeListener each : myListeners) {
            each.selectionChanged();
          }
        }

        @Override
        public void tabRemoved(TabInfo tabInfo) {
          for (TabChangeListener each : myListeners) {
            each.tabRemoved();
          }
        }
      });

      myTabs.setTabDraggingEnabled(true);
    }

    @Override
    public int getSelectedIndex() {
      return myTabs.getIndexOf(myTabs.getSelectedInfo());
    }

    @Override
    public void setSelectedIndex(int index) {
      myTabs.select(myTabs.getTabAt(index), true);
    }

    @Override
    public void setTabComponentAt(int index, Component component) {
      //nop
    }

    @Override
    public int indexOfComponent(Component component) {
      for (int i = 0; i<myTabs.getTabCount(); i++) {
        if (component.equals(myTabs.getTabAt(i).getComponent())) {
          return i;
        }
      }

      return -1;
    }

    @Override
    public int indexOfTabComponent(Component component) {
      return 0; //nop
    }


    private TabInfo getTabAt(int index) {
      checkIndex(index);
      return myTabs.getTabAt(index);
    }

    private void checkIndex(int index) {
      if (index < 0 || index >= getTabCount()) {
        throw new ArrayIndexOutOfBoundsException("tabCount=" + getTabCount() + " index=" + index);
      }
    }


    @Override
    public JediTerminalWidget getComponentAt(int i) {
      return (JediTerminalWidget)getTabAt(i).getComponent();
    }

    @Override
    public void addChangeListener(TabChangeListener listener) {
      myListeners.add(listener);
    }

    @Override
    public void setTitleAt(int index, String title) {
      getTabAt(index).setText(title);
    }

    @Override
    public void setSelectedComponent(JediTerminalWidget terminal) {
      TabInfo info = myTabs.findInfo(terminal);
      if (info != null) {
        myTabs.select(info, true);
      }
    }

    @Override
    public JComponent getComponent() {
      return myTabs.getComponent();
    }

    @Override
    public int getTabCount() {
      return myTabs.getTabCount();
    }

    @Override
    public void addTab(String name, JediTerminalWidget terminal) {
      myTabs.addTab(createTabInfo(name, terminal));
    }

    private TabInfo createTabInfo(String name, JediTerminalWidget terminal) {
      TabInfo tabInfo = new TabInfo(terminal).setText(name);
      return tabInfo;
    }

    public String getTitleAt(int i) {
      return getTabAt(i).getText();
    }

    public void removeAll() {
      myTabs.removeAllTabs();
    }

    @Override
    public void remove(JediTerminalWidget terminal) {
      TabInfo info = myTabs.findInfo(terminal);
      if (info != null) {
        myTabs.removeTab(info);
      }
    }

    private class TerminalTabLabel extends TabLabel {
      public TerminalTabLabel(final JBTabsImpl tabs, final TabInfo info) {
        super(tabs, info);

        setOpaque(false);

        setFocusable(false);

        SimpleColoredComponent label = myLabel;

        //add more space between the label and the button
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

        label.addMouseListener(new MouseAdapter() {

          @Override
          public void mouseReleased(MouseEvent event) {
            handleMouse(event);
          }

          @Override
          public void mousePressed(MouseEvent event) {
            handleMouse(event);
          }

          private void handleMouse(MouseEvent e) {
            if (e.isPopupTrigger()) {
              JPopupMenu menu = createPopup();
              menu.show(e.getComponent(), e.getX(), e.getY());
            }
            else if (e.getButton() != MouseEvent.BUTTON2) {
              myTabs.select(getInfo(), true);

              if (e.getClickCount() == 2 && !e.isConsumed()) {
                e.consume();
                renameTab();
              }
            }
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON2) {
              if (myTabs.getSelectedInfo() == info) {
                closeCurrentSession();
              }
              else {
                myTabs.select(info, true);
              }
            }
          }
        });
      }

      protected JPopupMenu createPopup() {
        JPopupMenu popupMenu = new JPopupMenu();

        TerminalAction.addToMenu(popupMenu, JediTabbedTerminalWidget.this);

        JMenuItem rename = new JMenuItem("Rename Tab");

        rename.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent actionEvent) {
            renameTab();
          }
        });

        popupMenu.add(rename);

        return popupMenu;
      }

      private void renameTab() {
        new TabRenamer() {
          @Override
          protected JTextField createTextField() {
            JTextField textField = new JTextField() {
              private int myMinimalWidth;

              @Override
              public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                if (size.width > myMinimalWidth) {
                  myMinimalWidth = size.width;
                }

                return wider(size, myMinimalWidth);
              }

              private Dimension wider(Dimension size, int minimalWidth) {
                return new Dimension(minimalWidth + 10, size.height);
              }
            };
            if (myTabs.useSmallLabels()) {
              textField.setFont(com.intellij.util.ui.UIUtil.getFont(UIUtil.FontSize.SMALL, textField.getFont()));
            }
            textField.setOpaque(true);
            return textField;
          }
        }.install(getSelectedIndex(), getInfo().getText(), myLabel, new TabRenamer.RenameCallBack() {
          @Override
          public void setComponent(Component c) {
//            myTabs.setTabDraggingEnabled(!(c instanceof JTextField));

            setPlaceholderContent(true, (JComponent)c);
          }

          @Override
          public void setNewName(int index, String name) {
            setTitleAt(index, name);
          }
        });
      }
    }


  }
}
