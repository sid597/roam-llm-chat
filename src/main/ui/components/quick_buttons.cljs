(ns ui.components.quick-buttons
  (:require [cljs.core.async.interop :as asy :refer [<p!]]
            [reagent.core :as r :refer [atom]]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [ui.extract-data.chat :as ed :refer [data-for-pages data-for-blocks]]
            [ui.components.chat :refer [chat-context chin]]
            [ui.components.graph-overview-ai :refer [filtered-pages-button]]
            [ui.utils :refer [q block-with-str-on-page? call-openai-api update-block-string uid->title log get-child-with-str get-child-of-child-with-str-on-page get-open-page-uid get-block-parent-with-order get-focused-block create-struct gen-new-uid default-chat-struct get-todays-uid]]
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
        ;(println "res " child-str)
        (swap! res str  (str child-str "\n"))))
    res))

(comment
  (get-child-of-child-with-str-on-page "LLM chat settings" "Quick action buttons" "Summarise this page" "Context"))


(defn button-with-settings [button-name]
  (let [get-linked-refs? (r/atom true)
        active? (r/atom false)
        default-msg-value (r/atom 400)
        default-temp (r/atom 0.9)
        default-model (r/atom "gpt-4-1106-preview")
        context (r/atom (get-child-of-child-with-str-on-page "LLM chat settings" "Quick action buttons" button-name "Context"))]
    (fn [_]
      #_(println "--" (get-child-of-child-with-str-on-page "llm chat" "Quick action buttons" button-name "Context"))
      [:> ButtonGroup
       {:class-name "button-with-settings"
        :style {:overflow "hidden"
                :display "flex"
                :flex-direction "row"
                :justify-content "space-between"
                :align-items "center"
                :flex "1 1 1"}
        :minimal true}
       [:div {:style {:flex "1 1 1"}}
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
          [chin default-model default-msg-value default-temp get-linked-refs? active?]]]]
       [:div {:style {:flex "1 1 1"}}
         [:> Button {:minimal true
                     :small true
                     :loading @active?
                     :on-click (fn [e]
                                 (when (not @active?)
                                   (reset! active? true)
                                  (go
                                   (let [current-page-uid    (<p! (get-open-page-uid))
                                         title               (uid->title current-page-uid)
                                         block-data          (when (nil? title)
                                                               (str
                                                                 "```"
                                                                 (clojure.string/join "\n -----" (data-for-blocks [current-page-uid]))
                                                                 "```"))
                                         already-summarised? (block-with-str-on-page? current-page-uid "AI summary")
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
                                                                         "LLM chat settings" "Quick action buttons" button-name "Context")))
                                         page-data           (when-not (nil? title) (data-for-pages [{:text (str title)}] get-linked-refs?))
                                         send-data           (if (nil? title)
                                                                 (str @context "\n" block-data)
                                                                 (str @context "\n" page-data))]
                                     (println "LLM chat:send data" send-data)
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
                                                                 (update-block-string
                                                                   res-block-uid
                                                                   (str res-str)
                                                                   (js/setTimeout
                                                                     (fn []
                                                                       (reset! active? false))
                                                                     500))))})))))))))}

          "Summarise this page"]]])))

