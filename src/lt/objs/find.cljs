(ns lt.objs.find
  (:require [lt.object :as object]
            [lt.objs.context :as ctx]
            [lt.util.load :as load]
            [lt.objs.canvas :as canvas]
            [lt.objs.sidebar.command :as cmd]
            [lt.objs.editor.pool :as pool]
            [lt.objs.keyboard :as keyboard]
            [lt.objs.editor :as editor]
            [lt.objs.tabs :as tabs]
            [lt.util.dom :as dom]
            [crate.binding :refer [bound subatom]]
            [lt.util.style :refer [->px]])
  (:require-macros [lt.macros :refer [defui]]))

(def find-height 30)

(defui input [this]
  [:input.find {:type "text"
                :placeholder "find"}]
  :keyup (fn []
           (this-as me
                    (object/raise this :search! (dom/val me))))
  :focus (fn []
           (ctx/in! :find-bar this)
           (object/raise bar :active))
  :blur (fn []
          (ctx/out! :find-bar)
          (object/raise bar :inactive)))

(defui replace-input [this]
  [:input.replace {:type "text"
                   :placeholder "replace"}]
  :keyup (fn []
           (this-as me
                    (object/raise this :replace.changed (dom/val me))))
  :focus (fn []
           (ctx/in! :find-bar.replace this)
           (object/raise bar :active))
  :blur (fn []
          (ctx/out! :find-bar.replace)
          (object/raise bar :inactive)))

(defui replace-all-button [this]
  [:button "all"]
  :click (fn []
           (cmd/exec! :find.replace-all)))

(defn current-ed []
  (editor/->cm-ed (pool/last-active)))

(defn ->shown-width [shown?]
  (if shown?
    ""
    "0"))

(defn ->val [this]
  (dom/val (dom/$ :input.find (object/->content this))))

(defn ->replacement [this]
  (dom/val (dom/$ :input.replace (object/->content this))))

(defn set-val [this v]
  (dom/val (dom/$ :input.find (object/->content this)) v))

(object/behavior* ::show!
                  :triggers #{:show!}
                  :reaction (fn [this]
                              (when-not (:shown @this)
                                (let [tabs (ctx/->obj :tabs)]
                                  (object/merge! this {:bottom (:bottom @tabs)
                                                       :left (:left @tabs)
                                                       :right (:right @tabs)
                                                       :shown true})
                                  (object/raise tabs :bottom! find-height)
                                  ))))

(object/behavior* ::adjust-find-on-resize
                  :triggers #{:resize}
                  :reaction (fn [this]
                              (when (:shown @bar)
                                (object/merge! bar {:bottom (- (:bottom @tabs/multi) find-height)}))))

(object/behavior* ::hide!
                  :triggers #{:hide!}
                  :reaction (fn [this]
                              (when (:shown @this)
                                (let [tabs (ctx/->obj :tabs)]
                                  (object/merge! this {:shown false})
                                  (object/raise tabs :bottom! (- find-height))
                                  (when-let [ed (pool/last-active)]
                                    (editor/focus ed))
                                  ))))

(object/behavior* ::next!
                  :triggers #{:next!}
                  :reaction (fn [this]
                              (when-let [cur (pool/last-active)]
                                (if (and (:searching? @this)
                                         (= (:searching.for @cur) (->val this)))
                                  (js/CodeMirror.commands.findNext (current-ed) (:reverse? @this))
                                  (object/raise this :search! (->val this))))))

(object/behavior* ::prev!
                  :triggers #{:prev!}
                  :reaction (fn [this]
                              (when-let [cur (pool/last-active)]
                                (if (and (:searching? @this)
                                         (= (:searching.for @cur) (->val this)))
                                  (js/CodeMirror.commands.findPrev (current-ed) (:reverse? @this))
                                  (object/raise this :search! (->val this))))))

(object/behavior* ::focus!
                  :triggers #{:focus!}
                  :reaction (fn [this]
                              (let [input (dom/$ :input (object/->content this))]
                                (dom/focus input)
                                (.select input))))

(object/behavior* ::clear!
                  :triggers #{:clear!}
                  :reaction (fn [this]
                              (object/merge! this {:searching? false})
                              (when-let [ed (pool/last-active)]
                                (js/CodeMirror.commands.clearSearch (editor/->cm-ed ed)))
                              (let [input (dom/$ :input (object/->content this))]
                                (when (= "" (dom/val input))
                                  (dom/val input "")))))


(object/behavior* ::replace!
                  :triggers #{:replace!}
                  :reaction (fn [this all?]
                              (when-not (:searching? @this)
                                (object/raise this :search! (->val this)))
                              (js/CodeMirror.commands.replace (editor/->cm-ed (pool/last-active)) (->replacement this) (:reverse? @this) (boolean all?))
                              (object/raise this :next!)))

(object/behavior* ::search!
                  :triggers #{:search!}
                  :debounce 50
                  :reaction (fn [this v]
                              (if (empty? v)
                                (object/raise this :clear!)
                                (when-let [e (pool/last-active)]
                                  (object/merge! this {:searching? true})
                                  (object/merge! e {:searching.for v})
                                  (let [ed (editor/->cm-ed e)]
                                    (js/CodeMirror.commands.find ed v (:reverse? @this)))))))

(object/object* ::find-bar
                :tags #{:find-bar}
                :bottom 0
                :left 0
                :searching? false
                :reverse? false
                :shown false
                :init (fn [this]
                        [:div#find-bar {:style {:bottom (bound (subatom this :bottom) ->px)
                                                :left (bound (subatom (ctx/->obj :tabs) :left) ->px)
                                                :right (bound (subatom (ctx/->obj :tabs) :right) ->px)
                                                :width (bound (subatom this :shown) ->shown-width)
                                                :height (bound (subatom this :shown) ->shown-width)}}
                         (input this)
                         (replace-input this)
                         (replace-all-button this)]))

(object/behavior* ::init
                  :triggers #{:init}
                  :reaction (fn [this]
                              (load/js "core/node_modules/codemirror/search.js" :sync)
                              (load/js "core/node_modules/codemirror/searchcursor.js" :sync)

                              ))

(def bar (object/create ::find-bar))

(canvas/add! bar)

(cmd/command {:command :find.show
              :desc "Find: In current editor"
              :exec (fn [rev?]
                      (object/merge! bar {:reverse? rev?})
                      (object/raise bar :show!)
                      (object/raise bar :focus!))})

(cmd/command {:command :find.fill-selection
              :desc "Find: Fill with selection"
              :exec (fn []
                      (when-let [e (pool/last-active)]
                        (when-let [sel (editor/selection e)]
                          (when-not (empty? sel)
                            (set-val bar sel)))))})

(cmd/command {:command :find.clear
              :desc "Find: Clear the find bar"
              :hidden true
              :exec (fn []
                      (object/raise bar :clear!))})

(cmd/command {:command :find.hide
              :desc "Find: Hide the find bar"
              :exec (fn []
                      (object/raise bar :hide!))})

(cmd/command {:command :find.next
              :desc "Find: Next find result"
              :exec (fn []
                      (object/raise bar :next!))})

(cmd/command {:command :find.prev
              :desc "Find: Previous find result"
              :exec (fn []
                      (object/raise bar :prev!))})

(cmd/command {:command :find.replace
              :desc "Find: Replace current"
              :exec (fn []
                      (object/raise bar :replace!))})

(cmd/command {:command :find.replace-all
              :desc "Find: Replace all occurrences"
              :exec (fn []
                      (object/raise bar :replace! :all))})

(def line-input (cmd/options-input {:placeholder "line number"}))

(object/behavior* ::exec-active!
                  :triggers #{:select}
                  :reaction (fn [this l]
                              (cmd/exec-active! l)))

(object/add-behavior! line-input ::exec-active!)

(cmd/command {:command :goto-line
              :desc "Editor: Goto line"
              :options line-input
              :exec (fn [l]
                      (when (or (number? l) (not (empty? l)))
                        (let [cur (pool/last-active)]
                          (editor/move-cursor cur {:ch 0
                                                   :line (dec (if-not (number? l)
                                                                (js/parseInt l)
                                                                l))})
                          (editor/center-cursor cur))))})