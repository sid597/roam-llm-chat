(ns ui.render-comp.bottom-bar
  (:require [applied-science.js-interop :as j]
            ["@blueprintjs/core" :as bp :refer [Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
            [ui.components.bottom-bar :as bcomp :refer [bottom-bar-buttons]]
            [reagent.dom :as rd]))


(defn bottom-bar []
  [:div.bottom-bar
   {:style {:display "flex"
            :flex-direction "row"
            :background-color "#eeebeb"
            :justify-content "center"
            :font-size "10px"
            :padding-right "11px"
            :align-items "center"
            :border "1px"}}
   [bottom-bar-buttons]])

(defn bottom-bar-main []
  (let [parent-el (.querySelector js/document ".roam-body")
        new-child (.createElement js/document "div")
        already-present? (.querySelector js/document ".llm-bottom-bar")]
    (set! (.-className new-child) "llm-bottom-bar")
    (.appendChild parent-el new-child)
    (rd/render [bottom-bar] new-child)
    (when (and
            (nil? already-present?)
            parent-el)
      (j/assoc-in! parent-el [:style :display] "flex")
      (j/assoc-in! parent-el [:style :flex-direction] "column"))))
