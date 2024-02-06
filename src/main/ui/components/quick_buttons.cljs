(ns ui.components.quick-buttons
  (:require [cljs.core.async.interop :as asy :refer [<p!]]
            [reagent.core :as r :refer [atom]]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [ui.extract-data.chat :as ed :refer [data-for-pages]]
            [ui.components.chat :refer [chat-context chin]]
            [ui.components.graph-overview-ai :refer [filtered-pages-button]]
            [ui.utils :refer [q call-openai-api update-block-string uid->title log get-child-with-str get-child-of-child-with-str-on-page get-open-page-uid get-block-parent-with-order get-focused-block create-struct gen-new-uid default-chat-struct get-todays-uid]]
            ["@blueprintjs/core" :as bp :refer [ControlGroup Checkbox Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]))


(defn button-popover
  ([render-comp]
   (button-popover render-comp "#eeebeb"))
  ([render-comp bg-color]
   [:> Popover
    ;{:position "bottom"}
    [:> Button {:icon "cog"
                :minimal true
                :small true
                :style {:background-color bg-color}}]
    [:> Menu
     {:style {:padding "20px"}}
     render-comp]]))


(defn extract-context-children-data-as-str [context]
  (let [res (r/atom "")
        children (sort-by :order (:children @context))]
    (doseq [child children]
      (let [child-str (:string child)]
        (println "res " child-str)
        (swap! res str  (str child-str "\n"))))
    res))


(defn handle-on-click [get-linked-refs? button-name])
  ;; create a new block in last position on the page
  ;; structure of the page should be "ai summary" and last block under that
  ;; then we extract the context append the page data,data and send it to the llm


(get-child-of-child-with-str-on-page "llm chat" "Quick action buttons" "Summarise this page" "Context")


(defn button-with-settings [button-name]
  (let [get-linked-refs? (r/atom true)
        active? (r/atom false)
        default-msg-value (r/atom 400)
        default-temp (r/atom 0.9)
        default-model (r/atom "gpt-4-1106-preview")
        context (r/atom (get-child-of-child-with-str-on-page "llm chat" "Quick action buttons" button-name "Context"))]
    (fn [_]
      (println "--" (get-child-of-child-with-str-on-page "llm chat" "Quick action buttons" button-name "Context"))
      [:> ButtonGroup
       {:minimal true
        :small true}
       [button-popover
        [:> Card {:elevation 3
                  :style {:flex "1"
                          :margin "0"
                          :display "flex"
                          :flex-direction "column"
                          :border "2px solid rgba(0, 0, 0, 0.2)"
                          :border-radius "8px"}}
         [:div.chat-input-container
          {:style {:display "flex"
                   :flex-direction "row"
                   :border-radius "8px"
                   :margin "10px 10px -10px 10px  "
                   :background-color "whitesmoke"
                   :border "1px"}}
          [chat-context context #()]]
         [chin default-model default-msg-value default-temp get-linked-refs? active?]]]
       [:> Button {:minimal true
                   :small true
                   :on-click (fn [e]
                               (go
                                 (let [current-page-uid    (<p! (get-open-page-uid))
                                       title               (uid->title current-page-uid)
                                       already-summarised? (:uid (get-child-with-str current-page-uid "AI summary"))
                                       parent-block-uid    (gen-new-uid)
                                       res-block-uid       (gen-new-uid)
                                       struct              (if (nil? already-summarised?)
                                                             {:s "AI summary"
                                                              :u parent-block-uid
                                                              :c [{:s ""
                                                                   :u res-block-uid}]}
                                                             {:s ""
                                                              :u res-block-uid})
                                       top-parent          (if (nil? already-summarised?)
                                                             current-page-uid
                                                             already-summarised?)
                                       context             (extract-context-children-data-as-str
                                                             (r/atom (get-child-of-child-with-str-on-page
                                                                       "llm chat" "Quick action buttons" button-name "Context")))
                                       page-data           (data-for-pages [{:text (str title)}] get-linked-refs?)
                                       send-data           (str @context "\n" page-data)]
                                   (do
                                     (cljs.pprint/pprint (str @context "\n" page-data))
                                     (create-struct struct top-parent res-block-uid false)
                                     (<p! (js/Promise.
                                            (fn [_]
                                              (call-openai-api
                                                {:messages [{:role "user"
                                                             :content send-data}]
                                                 :settings {:model @default-model
                                                            :max-tokens @default-msg-value
                                                            :temperature @default-temp}
                                                 :callback (fn [response]
                                                             (let [res-str (-> response
                                                                             :body)]
                                                               (update-block-string res-block-uid (str res-str))))}))))))))}

        "Summarise this page"]])))
